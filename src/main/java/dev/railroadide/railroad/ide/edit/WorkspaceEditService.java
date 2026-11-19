package dev.railroadide.railroad.ide.edit;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.ProjectDocumentSynchronizer;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditPreview.Target;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.editor.EditResult;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import javafx.application.Platform;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Validates, previews and applies {@link WorkspaceEdit}s for one project, and keeps the bounded history that undoes
 * and redoes each applied edit as one step. All methods must be called on the JavaFX application thread, which is
 * also the thread that publishes editor and discovered file changes; validation and application therefore run
 * without interleaving with other in-IDE publications. External processes can still change files at any time.
 * <p>
 * Application uses existing owners rather than a separate document model: open editors receive one undoable change
 * through {@link TextEditorAccess#replace}, unsaved drafts without an editor are republished as store overlays,
 * files without an editor or draft are written to disk and republished as backing, and renames reuse the IDE's
 * identity and editor relocation. See the {@code dev.railroadide.railroad.ide.sst.edit.api} package documentation
 * for the guarantees and limitations.
 */
public final class WorkspaceEditService implements TextEditorPane.SharedUndoHistory, AutoCloseable {
    private static final int HISTORY_LIMIT = 100;

    private final Path root;
    private final DocumentStore store;
    private final DocumentIdentityRegistry identities;
    private final WorkspaceEditorHost host;
    private final WorkspaceFiles files;
    private final LinkedList<HistoryEntry> history = new LinkedList<>();
    private final Set<TextEditorPane> attachedEditors = Collections.newSetFromMap(new WeakHashMap<>());
    private final Deque<WeakReference<Object>> revertedChanges = new ArrayDeque<>();
    private boolean busy;
    private boolean closed;

    /**
     * Creates a service for a project session.
     *
     * @param root project directory; only files inside it, outside directories Railroad does not manage, are changed
     * @param store project document store
     * @param identities project document identities
     * @param host owner of open editors and identity relocation
     */
    public WorkspaceEditService(
        Path root,
        DocumentStore store,
        DocumentIdentityRegistry identities,
        WorkspaceEditorHost host
    ) {
        this(root, store, identities, host, WorkspaceFiles.NIO);
    }

    /**
     * Creates a service whose filesystem operations go through the supplied implementation.
     *
     * @param root project directory
     * @param store project document store
     * @param identities project document identities
     * @param host owner of open editors and identity relocation
     * @param files filesystem operations, normally {@link WorkspaceFiles#NIO}
     */
    public WorkspaceEditService(
        Path root,
        DocumentStore store,
        DocumentIdentityRegistry identities,
        WorkspaceEditorHost host,
        WorkspaceFiles files
    ) {
        this.root = canonical(Objects.requireNonNull(root, "root"));
        this.store = Objects.requireNonNull(store, "store");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.host = Objects.requireNonNull(host, "host");
        this.files = Objects.requireNonNull(files, "files");
    }

    /**
     * Validates an edit against the current workspace without changing anything.
     *
     * @param edit edit to inspect
     * @return before/after text, file operations and any problems that would prevent application
     */
    public WorkspaceEditPreview preview(WorkspaceEdit edit) {
        requireUsable();
        Plan plan = plan(Objects.requireNonNull(edit, "edit"), Replay.NONE);
        List<WorkspaceEditPreview.DocumentPreview> documents = new ArrayList<>();
        for (TextTarget target : plan.texts) {
            if (target.kind == null || target.after == null)
                continue;
            List<WorkspaceEditPreview.ChangePreview> changes = new ArrayList<>();
            int delta = 0;
            for (Replacement replacement : target.replacements) {
                int afterStart = replacement.start + delta;
                changes.add(new WorkspaceEditPreview.ChangePreview(new TextRange(replacement.start, replacement.end),
                    new TextRange(afterStart, afterStart + replacement.text.length()), replacement.text,
                    replacement.annotationId));
                delta += replacement.text.length() - (replacement.end - replacement.start);
            }
            documents.add(new WorkspaceEditPreview.DocumentPreview(target.id, target.expected.uri(), target.kind,
                target.expected.text(), target.after, changes));
        }
        List<WorkspaceEditPreview.ResourcePreview> resources = new ArrayList<>();
        for (WorkspaceChange change : edit.changes()) {
            switch (change) {
                case WorkspaceChange.CreateFile create -> resources.add(new WorkspaceEditPreview.ResourcePreview(
                    WorkspaceEditPreview.ResourcePreview.Kind.CREATE, null, create.uri(), create.annotationId()));
                case WorkspaceChange.DeleteFile delete -> resources.add(new WorkspaceEditPreview.ResourcePreview(
                    WorkspaceEditPreview.ResourcePreview.Kind.DELETE, delete.expected().uri(), null,
                    delete.annotationId()));
                case WorkspaceChange.RenameFile rename -> resources.add(new WorkspaceEditPreview.ResourcePreview(
                    WorkspaceEditPreview.ResourcePreview.Kind.RENAME, rename.expected().uri(), rename.newUri(),
                    rename.annotationId()));
                case WorkspaceChange.TextEdit _ -> {
                }
            }
        }
        return new WorkspaceEditPreview(edit.label(), documents, resources, plan.problems);
    }

    /**
     * Validates and applies an edit as one transaction and records it for undo. Nothing is changed if validation
     * fails. If a step fails after mutation began, completed steps are reverted in reverse order.
     *
     * @param edit edit to apply
     * @return outcome, including every problem and any location that could not be reverted
     * @throws IllegalStateException if called off the JavaFX thread, re-entrantly, or after closure
     */
    public WorkspaceEditResult apply(WorkspaceEdit edit) {
        requireUsable();
        Objects.requireNonNull(edit, "edit");
        busy = true;
        try {
            Plan plan = plan(edit, Replay.NONE);
            if (!plan.problems.isEmpty())
                return new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED, plan.problems);
            WorkspaceEditResult result = execute(plan);
            HistoryEntry entry = HistoryEntry.from(edit.label(), plan);
            if (result.applied() && (!entry.texts.isEmpty() || !entry.resources.isEmpty())) {
                remember(entry);
            }
            return result;
        } finally {
            busy = false;
        }
    }

    /**
     * Reports whether an applied edit is recorded for undo.
     *
     * @return whether {@link #undo()} has an edit to revert
     */
    public boolean canUndo() {
        return history.stream().anyMatch(entry -> !entry.undone);
    }

    /**
     * Reports whether an undone edit is recorded for redo.
     *
     * @return whether {@link #redo()} has an edit to reapply
     */
    public boolean canRedo() {
        return history.stream().anyMatch(entry -> entry.undone);
    }

    /**
     * Reverts the most recently applied or redone edit as one transaction. Every affected document must still hold
     * exactly the content the edit produced. Otherwise nothing is changed and the edit is removed from history,
     * since it can no longer be undone as a whole. An edit whose undo failed only because files could not be read or
     * written, or failed and was rolled back, is kept for retry.
     *
     * @return outcome; {@link WorkspaceEditResult.Status#REJECTED} with no problems when there is nothing to undo
     */
    public WorkspaceEditResult undo() {
        requireUsable();
        return history.stream().filter(entry -> !entry.undone).findFirst()
            .map(this::step)
            .orElseGet(() -> new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED, List.of()));
    }

    /**
     * Reapplies the most recently undone edit as one transaction, under the same conditions as {@link #undo()}.
     * Undone edits stay redoable after later edits for as long as every affected document is unchanged.
     *
     * @return outcome; {@link WorkspaceEditResult.Status#REJECTED} with no problems when there is nothing to redo
     */
    public WorkspaceEditResult redo() {
        requireUsable();
        return history.stream().filter(entry -> entry.undone).findFirst()
            .map(this::step)
            .orElseGet(() -> new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED, List.of()));
    }

    private WorkspaceEditResult step(HistoryEntry entry) {
        WorkspaceEditResult result = transition(entry);
        boolean transientFailure = result.problems().stream()
            .allMatch(problem -> problem.kind() == WorkspaceEditProblem.Kind.IO_FAILURE);
        if (result.status() == WorkspaceEditResult.Status.INCONSISTENT
            || result.status() == WorkspaceEditResult.Status.REJECTED && !transientFailure) {
            history.remove(entry);
        }
        return result;
    }

    /**
     * Undoes the workspace edit that owns the editor's next undoable change. If any other affected document has
     * changed, nothing is changed, the edit is removed from shared history and a warning is logged, so that the
     * next request performs the editor's local undo instead.
     *
     * @param editor editor whose undo was requested
     * @return whether the request belonged to a workspace edit
     */
    @Override
    public boolean undo(TextEditorPane editor) {
        return route(editor, false);
    }

    /**
     * Redoes the workspace edit that owns the editor's next redoable change, with the same refusal behaviour as
     * {@link #undo(TextEditorPane)}.
     *
     * @param editor editor whose redo was requested
     * @return whether the request belonged to a workspace edit
     */
    @Override
    public boolean redo(TextEditorPane editor) {
        return route(editor, true);
    }

    /** Discards history and detaches from editors. Further use is rejected. */
    @Override
    public void close() {
        closed = true;
        history.clear();
        for (TextEditorPane editor : List.copyOf(attachedEditors)) {
            if (editor.getSharedUndoHistory() == this) {
                editor.setSharedUndoHistory(null);
            }
        }
        attachedEditors.clear();
    }

    private void requireUsable() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Workspace edits require the JavaFX application thread");
        if (closed)
            throw new IllegalStateException("Workspace edit service is closed");
        if (busy)
            throw new IllegalStateException("Workspace edits cannot be applied re-entrantly");
    }

    private boolean route(TextEditorPane editor, boolean redo) {
        if (closed || busy || !Platform.isFxApplicationThread())
            return false;
        Object next = redo ? editor.getUndoManager().getNextRedo() : editor.getUndoManager().getNextUndo();
        if (next == null)
            return false;
        if (redo && revertedChanges.stream().anyMatch(reference -> reference.get() == next)) {
            Railroad.LOGGER.warn("Not redoing an editor change that belonged to a rolled-back workspace edit");
            return true;
        }
        HistoryEntry owner = null;
        for (HistoryEntry entry : history) {
            for (TextRecord record : entry.texts) {
                if (record.editor.get() == editor && record.token == next && record.tokenOnRedo == redo) {
                    owner = entry;
                }
            }
            if (owner != null)
                break;
        }
        if (owner == null)
            return false;
        WorkspaceEditResult result = transition(owner);
        if (!result.applied()) {
            history.remove(owner);
            Railroad.LOGGER.warn("Could not {} '{}' across all affected documents: {}. Repeat the command to {} "
                + "only this editor.", owner.undone ? "redo" : "undo", owner.label, result.problems(),
                redo ? "redo" : "undo");
        }
        return true;
    }

    private WorkspaceEditResult transition(HistoryEntry entry) {
        busy = true;
        try {
            boolean undo = !entry.undone;
            List<WorkspaceEditProblem> problems = new ArrayList<>();
            WorkspaceEdit.Builder builder = WorkspaceEdit.builder(entry.label);
            var replay = new Replay(new HashMap<>(), new HashMap<>(), new HashMap<>());
            int index = 0;
            for (TextRecord record : entry.texts) {
                DocumentSnapshot current = currentSnapshot(record.id);
                String target = current instanceof TextDocumentSnapshot text
                    ? replayTarget(text.text(), undo ? record.after : record.before,
                        undo ? record.before : record.after)
                    : null;
                if (target == null) {
                    problems.add(new WorkspaceEditProblem(WorkspaceEditProblem.Kind.STALE, -1,
                        current == null ? null : current.uri(), "Document changed after '" + entry.label + "'"));
                    continue;
                }
                var text = (TextDocumentSnapshot) current;
                builder.change(new WorkspaceChange.TextEdit(text, minimalReplacement(text.text(), target)));
                index++;
                TextEditorPane editor = record.editor.get();
                if (editor != null && record.token != null) {
                    replay.natives.put(record.id, new NativeStep(editor, record.token, record.tokenOnRedo));
                }
            }
            List<ResourceRecord> resources = new ArrayList<>(entry.resources);
            if (undo) {
                Collections.reverse(resources);
            }
            for (ResourceRecord record : resources) {
                WorkspaceChange change = inverseOrReplay(record, undo, problems);
                if (change == null)
                    continue;
                builder.change(change);
                boolean removesCreation = undo && record.kind != WorkspaceEditPreview.ResourcePreview.Kind.DELETE;
                if (removesCreation && !record.createdDirectories.isEmpty()) {
                    replay.cleanup.put(index, record.createdDirectories);
                }
                if (change instanceof WorkspaceChange.CreateFile && record.id != null) {
                    replay.identities.put(index, record.id);
                }
                index++;
            }
            if (!problems.isEmpty())
                return new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED, problems);

            Plan plan = plan(builder.build(), replay);
            if (!plan.problems.isEmpty())
                return new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED, plan.problems);
            WorkspaceEditResult result = execute(plan);
            if (!result.applied())
                return result;

            entry.update(plan, undo);
            history.remove(entry);
            history.addFirst(entry);
            for (TextRecord record : entry.texts) {
                host.openEditor(record.id).ifPresent(editor -> {
                    TextSelection selection = undo ? record.selectionBefore : record.selectionAfter;
                    if (selection != null && selection.anchor() <= editor.getLength()
                        && selection.caret() <= editor.getLength()) {
                        editor.selectRange(selection.anchor(), selection.caret());
                    }
                });
            }
            return result;
        } finally {
            busy = false;
        }
    }

    private WorkspaceChange inverseOrReplay(ResourceRecord record, boolean undo, List<WorkspaceEditProblem> problems) {
        boolean recreate = (record.kind == WorkspaceEditPreview.ResourcePreview.Kind.CREATE) != undo;
        return switch (record.kind) {
            case RENAME -> {
                DocumentUri from = undo ? record.target : record.source;
                DocumentSnapshot current = currentSnapshot(record.id);
                if (current == null || !sameLocation(current.uri(), from)) {
                    problems.add(new WorkspaceEditProblem(WorkspaceEditProblem.Kind.STALE, -1, from,
                        "Moved document is no longer at " + from));
                    yield null;
                }
                yield new WorkspaceChange.RenameFile(current, undo ? record.source : record.target, null);
            }
            case CREATE, DELETE -> {
                DocumentUri location = record.kind == WorkspaceEditPreview.ResourcePreview.Kind.CREATE
                    ? record.target
                    : record.source;
                if (recreate)
                    yield new WorkspaceChange.CreateFile(location, record.content, null);
                DocumentSnapshot current = currentSnapshot(record.id);
                if (current == null || !sameLocation(current.uri(), location)
                    || !contentEquals(current, record.content)) {
                    problems.add(new WorkspaceEditProblem(WorkspaceEditProblem.Kind.STALE, -1, location,
                        "File changed after it was created or restored"));
                    yield null;
                }
                yield new WorkspaceChange.DeleteFile(current, null);
            }
        };
    }

    private void remember(HistoryEntry entry) {
        history.addFirst(entry);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private DocumentSnapshot currentSnapshot(DocumentId id) {
        Optional<TextEditorPane> editor = host.openEditor(id);
        if (editor.isPresent()) {
            try {
                return editor.get().textEditorAccess().snapshot();
            } catch (IllegalStateException exception) {
                return null;
            }
        }
        return store.current(id).orElse(null);
    }

    private Plan plan(WorkspaceEdit edit, Replay replay) {
        var plan = new Plan(edit);
        Map<DocumentId, TextTarget> texts = new LinkedHashMap<>();
        Set<DocumentId> moved = new HashSet<>();
        Map<DocumentId, Integer> deleted = new HashMap<>();
        Set<Path> vacated = new HashSet<>();
        Set<Path> occupied = new HashSet<>();
        List<WorkspaceChange> changes = edit.changes();
        for (int index = 0; index < changes.size(); index++) {
            switch (changes.get(index)) {
                case WorkspaceChange.TextEdit textEdit -> {
                    TextTarget target = texts.get(textEdit.expected().id());
                    if (target == null) {
                        target = resolveText(plan, index, textEdit.expected(),
                            replay.natives.get(textEdit.expected().id()));
                        texts.put(target.id, target);
                    } else if (!sameSnapshot(target.expected, textEdit.expected())) {
                        plan.problem(WorkspaceEditProblem.Kind.CONFLICT, index, textEdit.expected().uri(),
                            "Edits for one document were computed against different revisions");
                        continue;
                    }
                    for (TextReplacement replacement : textEdit.replacements()) {
                        target.replacements.add(new Replacement(replacement.range().start, replacement.range().end,
                            replacement.text(), textEdit.annotationId(), index));
                    }
                }
                case WorkspaceChange.CreateFile create -> {
                    if (planCreate(plan, index, create, moved, deleted, vacated, occupied)) {
                        plan.resources.getLast().restoreId = restorableIdentity(replay.identities.get(index));
                    }
                }
                case WorkspaceChange.DeleteFile delete -> {
                    if (planDelete(plan, index, delete, moved, deleted, vacated, occupied)) {
                        plan.resources.getLast().cleanup = replay.cleanup.getOrDefault(index, List.of());
                    }
                }
                case WorkspaceChange.RenameFile rename -> {
                    if (planRename(plan, index, rename, moved, deleted, vacated, occupied)) {
                        plan.resources.getLast().cleanup = replay.cleanup.getOrDefault(index, List.of());
                    }
                }
            }
        }
        for (TextTarget target : texts.values()) {
            plan.texts.add(target);
            if (deleted.containsKey(target.id)) {
                plan.problem(WorkspaceEditProblem.Kind.CONFLICT, deleted.get(target.id), target.expected.uri(),
                    "A deleted document cannot also be edited");
            }
            if (target.kind != null) {
                computeAfter(plan, target);
            }
        }
        PostApplySelection selection = edit.selection();
        if (selection != null) {
            TextTarget target = texts.get(selection.document());
            Integer length = host.openEditor(selection.document())
                .map(editor -> target != null && target.after != null ? target.after.length() : editor.getLength())
                .orElse(null);
            if (length != null
                && (selection.selection().anchor() > length || selection.selection().caret() > length)) {
                plan.problem(WorkspaceEditProblem.Kind.INVALID, -1, null,
                    "Requested selection is outside the edited document");
            }
        }
        return plan;
    }

    private TextTarget resolveText(Plan plan, int index, TextDocumentSnapshot expected, NativeStep nativeStep) {
        var target = new TextTarget(expected);
        if (readOnlyOrigin(expected)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, expected.uri(),
                "Generated, decompiled and attached sources are read-only");
            return target;
        }
        Optional<TextEditorPane> openEditor = host.openEditor(expected.id());
        if (openEditor.isPresent()) {
            TextEditorPane editor = openEditor.get();
            TextEditorAccess access = editor.textEditorAccess();
            TextDocumentSnapshot current;
            try {
                current = access.snapshot();
            } catch (IllegalStateException exception) {
                plan.problem(WorkspaceEditProblem.Kind.STALE, index, expected.uri(),
                    "Editor content is not reconciled with its published revision");
                return target;
            }
            if (!sameSnapshot(expected, current)) {
                plan.problem(WorkspaceEditProblem.Kind.STALE, index, expected.uri(),
                    "Editor revision " + current.version() + " differs from expected " + expected.version());
                return target;
            }
            if (!access.isEditable()) {
                plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, expected.uri(), "Editor is read-only");
                return target;
            }
            if (expected.uri().isFile() && workspacePath(plan, index, expected.uri()) == null)
                return target;
            target.kind = Target.EDITOR;
            target.editor = editor;
            if (nativeStep != null && nativeStep.editor == editor && (nativeStep.redo
                ? editor.getUndoManager().getNextRedo() == nativeStep.token
                : editor.getUndoManager().getNextUndo() == nativeStep.token)) {
                target.nativeStep = nativeStep;
            }
            return target;
        }

        DocumentState state = store.state(expected.id()).orElse(null);
        DocumentSnapshot current = state == null ? null : state.overlay() != null ? state.overlay() : state.backing();
        if (current == null) {
            plan.problem(WorkspaceEditProblem.Kind.MISSING, index, expected.uri(), "Document is not published");
            return target;
        }
        if (!sameSnapshot(expected, current)) {
            plan.problem(WorkspaceEditProblem.Kind.STALE, index, expected.uri(),
                "Document revision " + current.version() + " differs from expected " + expected.version());
            return target;
        }
        target.state = state;
        if (state.overlay() != null) {
            if (expected.uri().isFile() && workspacePath(plan, index, expected.uri()) == null)
                return target;
            target.kind = Target.DRAFT;
            return target;
        }
        Path path = workspacePath(plan, index, expected.uri());
        if (path == null)
            return target;
        if (!files.exists(path)) {
            plan.problem(WorkspaceEditProblem.Kind.MISSING, index, expected.uri(), "File no longer exists");
            return target;
        }
        if (!diskMatches(plan, index, path, expected))
            return target;
        if (!files.isWritable(path)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, expected.uri(), "File is not writable");
            return target;
        }
        target.kind = Target.DISK;
        target.path = path;
        return target;
    }

    private void computeAfter(Plan plan, TextTarget target) {
        target.replacements.sort(Comparator.comparingInt(Replacement::start));
        String text = target.expected.text();
        int previousStart = -1;
        int previousEnd = -1;
        var after = new StringBuilder(text.length());
        int copied = 0;
        for (Replacement replacement : target.replacements) {
            if (replacement.end > text.length()) {
                plan.problem(WorkspaceEditProblem.Kind.OVERLAP, replacement.changeIndex, target.expected.uri(),
                    "Replacement " + new TextRange(replacement.start, replacement.end) + " is outside the document");
                return;
            }
            if (replacement.start < previousEnd || replacement.start == previousStart) {
                plan.problem(WorkspaceEditProblem.Kind.OVERLAP, replacement.changeIndex, target.expected.uri(),
                    "Replacement " + new TextRange(replacement.start, replacement.end)
                        + " overlaps another replacement or shares its start offset");
                return;
            }
            after.append(text, copied, replacement.start).append(replacement.text);
            copied = replacement.end;
            previousStart = replacement.start;
            previousEnd = replacement.end;
        }
        target.after = after.append(text, copied, text.length()).toString();
        if (!target.expected.encoding().newEncoder().canEncode(target.after)) {
            plan.problem(WorkspaceEditProblem.Kind.INVALID, target.replacements.getFirst().changeIndex,
                target.expected.uri(), "Resulting text cannot be encoded as " + target.expected.encoding().name());
        }
    }

    private boolean planCreate(
        Plan plan,
        int index,
        WorkspaceChange.CreateFile create,
        Set<DocumentId> moved,
        Map<DocumentId, Integer> deleted,
        Set<Path> vacated,
        Set<Path> occupied
    ) {
        Path path = workspacePath(plan, index, create.uri());
        if (path == null)
            return false;
        if (willExist(path, vacated, occupied)) {
            plan.problem(WorkspaceEditProblem.Kind.ALREADY_EXISTS, index, create.uri(), "File already exists");
            return false;
        }
        Optional<DocumentIdentity> identity = identities.findIdentity(create.uri());
        DocumentId previous = identity.map(DocumentIdentity::id).orElse(null);
        if (previous != null && !moved.contains(previous) && !deleted.containsKey(previous)
            && store.state(previous).isPresent()) {
            plan.problem(WorkspaceEditProblem.Kind.CONFLICT, index, create.uri(),
                "A document, such as an unsaved draft, is still published at this location");
            return false;
        }
        if (!parentWritable(path)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, create.uri(), "Directory is not writable");
            return false;
        }
        byte[] content = create.content();
        try {
            ProjectDocumentSynchronizer.backingSnapshot(DocumentId.create(), create.uri(), DocumentVersion.initial(),
                path, content);
        } catch (CharacterCodingException exception) {
            plan.problem(WorkspaceEditProblem.Kind.INVALID, index, create.uri(), "Text file content is not UTF-8");
            return false;
        }
        vacated.remove(path);
        occupied.add(path);
        var step = new ResourceStep(WorkspaceEditPreview.ResourcePreview.Kind.CREATE);
        step.target = path;
        step.targetUri = create.uri();
        step.content = content;
        plan.resources.add(step);
        return true;
    }

    private boolean planDelete(
        Plan plan,
        int index,
        WorkspaceChange.DeleteFile delete,
        Set<DocumentId> moved,
        Map<DocumentId, Integer> deleted,
        Set<Path> vacated,
        Set<Path> occupied
    ) {
        DocumentSnapshot expected = delete.expected();
        if (moved.contains(expected.id()) || deleted.containsKey(expected.id())) {
            plan.problem(WorkspaceEditProblem.Kind.CONFLICT, index, expected.uri(),
                "A document can be moved or deleted only once per edit");
            return false;
        }
        deleted.put(expected.id(), index);
        if (host.isOpen(expected.id())) {
            plan.problem(WorkspaceEditProblem.Kind.IN_USE, index, expected.uri(),
                "Close the document's editor before deleting it");
            return false;
        }
        DocumentState state = store.state(expected.id()).orElse(null);
        if (state != null && state.overlay() != null) {
            plan.problem(WorkspaceEditProblem.Kind.IN_USE, index, expected.uri(),
                "The document has an unsaved draft");
            return false;
        }
        if (state == null || state.backing() == null) {
            plan.problem(WorkspaceEditProblem.Kind.MISSING, index, expected.uri(), "Document is not published");
            return false;
        }
        if (!sameSnapshot(expected, state.backing())) {
            plan.problem(WorkspaceEditProblem.Kind.STALE, index, expected.uri(),
                "Document revision " + state.backing().version() + " differs from expected " + expected.version());
            return false;
        }
        if (readOnlyOrigin(expected)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, expected.uri(),
                "Generated, decompiled and attached sources are read-only");
            return false;
        }
        Path path = workspacePath(plan, index, expected.uri());
        if (path == null)
            return false;
        if (occupied.contains(path) || !willExist(path, vacated, occupied)) {
            plan.problem(WorkspaceEditProblem.Kind.MISSING, index, expected.uri(), "File no longer exists");
            return false;
        }
        if (!diskMatches(plan, index, path, expected))
            return false;
        if (!parentWritable(path)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, expected.uri(), "Directory is not writable");
            return false;
        }
        vacated.add(path);
        var step = new ResourceStep(WorkspaceEditPreview.ResourcePreview.Kind.DELETE);
        step.id = expected.id();
        step.expected = expected;
        step.state = state;
        step.source = path;
        step.sourceUri = expected.uri();
        step.content = contentBytes(expected);
        plan.resources.add(step);
        return true;
    }

    private boolean planRename(
        Plan plan,
        int index,
        WorkspaceChange.RenameFile rename,
        Set<DocumentId> moved,
        Map<DocumentId, Integer> deleted,
        Set<Path> vacated,
        Set<Path> occupied
    ) {
        DocumentSnapshot expected = rename.expected();
        if (moved.contains(expected.id()) || deleted.containsKey(expected.id())) {
            plan.problem(WorkspaceEditProblem.Kind.CONFLICT, index, expected.uri(),
                "A document can be moved or deleted only once per edit");
            return false;
        }
        moved.add(expected.id());
        DocumentSnapshot current = currentSnapshot(expected.id());
        boolean editorOpen = host.openEditor(expected.id()).isPresent();
        if (current == null) {
            plan.problem(WorkspaceEditProblem.Kind.MISSING, index, expected.uri(), "Document is not published");
            return false;
        }
        if (!sameSnapshot(expected, current)) {
            plan.problem(WorkspaceEditProblem.Kind.STALE, index, expected.uri(),
                "Document revision " + current.version() + " differs from expected " + expected.version());
            return false;
        }
        if (readOnlyOrigin(expected)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, expected.uri(),
                "Generated, decompiled and attached sources are read-only");
            return false;
        }
        Path source = workspacePath(plan, index, expected.uri());
        Path target = workspacePath(plan, index, rename.newUri());
        if (source == null || target == null)
            return false;
        boolean caseOnly = source.equals(target);
        if (caseOnly && source.toString().equals(target.toString())) {
            plan.problem(WorkspaceEditProblem.Kind.INVALID, index, rename.newUri(),
                "Destination is the document's current location");
            return false;
        }
        if (occupied.contains(source) || !willExist(source, vacated, occupied)) {
            plan.problem(WorkspaceEditProblem.Kind.MISSING, index, expected.uri(), "File no longer exists");
            return false;
        }
        if (!caseOnly && willExist(target, vacated, occupied)) {
            plan.problem(WorkspaceEditProblem.Kind.ALREADY_EXISTS, index, rename.newUri(), "Destination exists");
            return false;
        }
        Optional<DocumentIdentity> targetIdentity = identities.findIdentity(rename.newUri());
        DocumentId occupant = targetIdentity.map(DocumentIdentity::id).orElse(null);
        if (occupant != null && !occupant.equals(expected.id()) && !moved.contains(occupant)
            && !deleted.containsKey(occupant)
            && (store.state(occupant).isPresent() || host.openEditor(occupant).isPresent())) {
            plan.problem(WorkspaceEditProblem.Kind.CONFLICT, index, rename.newUri(),
                "Another document is still published at the destination");
            return false;
        }
        if (!editorOpen) {
            DocumentSnapshot onDisk = store.state(expected.id()).map(DocumentState::backing).orElse(null);
            if (onDisk != null && !diskMatches(plan, index, source, onDisk))
                return false;
        }
        if (!parentWritable(source) || !parentWritable(target)) {
            plan.problem(WorkspaceEditProblem.Kind.READ_ONLY, index, rename.newUri(), "Directory is not writable");
            return false;
        }
        vacated.add(source);
        vacated.remove(target);
        occupied.add(target);
        var step = new ResourceStep(WorkspaceEditPreview.ResourcePreview.Kind.RENAME);
        step.id = expected.id();
        step.expected = expected;
        step.source = source;
        step.sourceUri = expected.uri();
        step.target = target;
        step.sourceIdentity = identities.findIdentity(expected.uri())
            .filter(identity -> identity.id().equals(expected.id()))
            .orElse(new DocumentIdentity(expected.id(), expected.uri()));
        step.caseOnly = caseOnly;
        step.targetUri = rename.newUri();
        plan.resources.add(step);
        return true;
    }

    private WorkspaceEditResult execute(Plan plan) {
        Deque<Compensation> journal = new ArrayDeque<>();
        try {
            for (TextTarget target : plan.texts) {
                if (target.kind == Target.DISK && target.changes()) {
                    writeDiskText(target, journal);
                }
            }
            for (ResourceStep step : plan.resources) {
                switch (step.kind) {
                    case CREATE -> {
                        createParents(step, journal);
                        createFile(step, journal);
                    }
                    case DELETE -> {
                        byte[] before = io(step.sourceUri, () -> files.read(step.source));
                        if (!Arrays.equals(before, step.content))
                            throw new StepFailure(WorkspaceEditProblem.Kind.STALE, step.sourceUri,
                                "File changed on disk before it could be deleted");
                        step.alias = identities.findAlias(step.id, step.source).orElse(null);
                        io(step.sourceUri, () -> files.delete(step.source));
                        journal.push(new Compensation(step.sourceUri, "restore deleted " + step.source, () -> {
                            if (step.alias == null) {
                                files.createFile(step.source, step.content);
                            } else if (Arrays.equals(files.read(step.alias), step.content)) {
                                files.createLink(step.source, step.alias);
                            } else
                                throw new IOException("Hard link " + step.alias + " changed after " + step.source
                                    + " was deleted");
                        }));
                        removeEmptyDirectories(step, journal);
                    }
                    case RENAME -> {
                        createParents(step, journal);
                        var moveBack = new Compensation(step.sourceUri, "move " + step.target + " back",
                            () -> moveBack(step));
                        // A case-only move can fail after leaving the file at a temporary name, so it is journaled
                        // first. A failed plain move has not moved anything that could be restored.
                        if (step.caseOnly) {
                            journal.push(moveBack);
                        }
                        io(step.targetUri, () -> move(step.source, step.target, step));
                        if (!step.caseOnly) {
                            journal.push(moveBack);
                        }
                        removeEmptyDirectories(step, journal);
                    }
                }
            }

            for (TextTarget target : plan.texts) {
                if (!target.changes() && target.kind != Target.EDITOR) {
                    target.applied = target.expected;
                    continue;
                }
                switch (target.kind) {
                    case DISK -> publishDiskText(target, journal);
                    case DRAFT -> publishDraft(target, journal);
                    case EDITOR -> applyToEditor(target, journal);
                    case null -> throw new IllegalStateException("Unresolved target");
                }
            }
            for (ResourceStep step : plan.resources) {
                switch (step.kind) {
                    case CREATE -> publishCreated(step, journal);
                    case DELETE -> unpublishDeleted(step, journal);
                    case RENAME -> relocate(step, journal);
                }
            }
        } catch (StepFailure failure) {
            return rolledBack(failure.problem, journal);
        } catch (RuntimeException failure) {
            Railroad.LOGGER.error("Workspace edit '{}' failed while publishing", plan.edit.label(), failure);
            return rolledBack(new WorkspaceEditProblem(WorkspaceEditProblem.Kind.CONFLICT, -1, null,
                "Publication failed: " + failure.getMessage()), journal);
        }

        for (TextTarget target : plan.texts) {
            if (target.editor != null) {
                target.selectionAfter = target.editor.textEditorAccess().selection();
            }
        }
        PostApplySelection selection = plan.edit.selection();
        if (selection != null) {
            host.openEditor(selection.document()).ifPresent(editor -> {
                TextEditorAccess access = editor.textEditorAccess();
                EditResult result;
                try {
                    result = access.selectAndReveal(access.snapshot(), selection.selection());
                } catch (IllegalStateException exception) {
                    result = EditResult.UNAVAILABLE;
                }
                if (result != EditResult.APPLIED) {
                    Railroad.LOGGER.warn("Applied '{}' but could not place the requested selection: {}",
                        plan.edit.label(), result);
                }
                TextTarget target = plan.target(selection.document());
                if (target != null && target.editor == editor) {
                    target.selectionAfter = access.selection();
                }
            });
        }
        return new WorkspaceEditResult(WorkspaceEditResult.Status.APPLIED, List.of());
    }

    private void writeDiskText(TextTarget target, Deque<Compensation> journal) throws StepFailure {
        DocumentUri uri = target.expected.uri();
        byte[] before = io(uri, () -> files.read(target.path));
        if (!matches(before, target.expected))
            throw new StepFailure(WorkspaceEditProblem.Kind.STALE, uri, "File changed on disk before writing");
        byte[] after = encode(target.after, target.expected.encoding(), uri);
        var written = new boolean[1];
        // Journaled first: a failed overwrite may already have truncated the file.
        journal.push(new Compensation(uri, "restore " + target.path, () -> {
            if (written[0] && !Arrays.equals(files.read(target.path), after))
                throw new IOException("File changed on disk after the edit was written");
            files.overwrite(target.path, before);
        }));
        io(uri, () -> files.overwrite(target.path, after));
        written[0] = true;
    }

    private void publishDiskText(TextTarget target, Deque<Compensation> journal) {
        TextDocumentSnapshot next = withText(target.expected, target.after, nextVersion(target.id));
        store.publishBacking(next, target.state);
        target.applied = next;
        journal.push(new Compensation(target.expected.uri(), "republish " + target.expected.uri(),
            () -> store.publishBacking(withText(target.expected, target.expected.text(), nextVersion(target.id)))));
    }

    private void publishDraft(TextTarget target, Deque<Compensation> journal) {
        TextDocumentSnapshot next = withText(target.expected, target.after, nextVersion(target.id));
        store.publishOverlay(next);
        target.applied = next;
        journal.push(new Compensation(target.expected.uri(), "restore draft " + target.expected.uri(),
            () -> store.publishOverlay(withText(target.expected, target.expected.text(), nextVersion(target.id)))));
    }

    private void applyToEditor(TextTarget target, Deque<Compensation> journal) {
        TextEditorPane editor = target.editor;
        TextEditorAccess access = editor.textEditorAccess();
        var undoManager = editor.getUndoManager();
        target.selectionBefore = access.selection();
        if (target.nativeStep != null) {
            boolean performed = target.nativeStep.redo ? undoManager.redo() : undoManager.undo();
            if (!performed)
                throw new IllegalStateException("Editor history changed during the edit");
            target.token = target.nativeStep.token;
            target.tokenOnRedo = !target.nativeStep.redo;
        } else {
            Object previous = undoManager.getNextUndo();
            List<TextReplacement> replacements = target.replacements.stream()
                .map(replacement -> new TextReplacement(new TextRange(replacement.start, replacement.end),
                    replacement.text))
                .toList();
            EditResult result = access.replace(target.expected, replacements);
            if (result != EditResult.APPLIED)
                throw new IllegalStateException("Editor rejected the edit: " + result);
            Object next = undoManager.getNextUndo();
            target.token = next == previous ? null : next;
            target.tokenOnRedo = false;
        }
        attach(editor);
        journal.push(new Compensation(target.expected.uri(), "revert editor " + target.expected.uri(), () -> {
            boolean reverted = false;
            if (target.token != null && target.tokenOnRedo && undoManager.getNextRedo() == target.token) {
                reverted = undoManager.redo();
            } else if (target.token != null && !target.tokenOnRedo && undoManager.getNextUndo() == target.token) {
                reverted = undoManager.undo();
                rememberReverted(target.token);
            }
            if (!reverted) {
                TextDocumentSnapshot current = access.snapshot();
                EditResult result = access.replace(current,
                    minimalReplacement(current.text(), target.expected.text()));
                if (result != EditResult.APPLIED)
                    throw new IllegalStateException("Editor rejected the revert: " + result);
            }
            if (target.selectionBefore.anchor() <= editor.getLength()
                && target.selectionBefore.caret() <= editor.getLength()) {
                editor.selectRange(target.selectionBefore.anchor(), target.selectionBefore.caret());
            }
        }));
        target.applied = access.snapshot();
        if (target.nativeStep != null && !target.applied.text().equals(target.after))
            throw new IllegalStateException("Editor history no longer reproduces the recorded text");
    }

    private void publishCreated(ResourceStep step, Deque<Compensation> journal) throws StepFailure {
        DocumentUri uri = DocumentUri.fromPath(step.target);
        DocumentId occupant = identities.findIdentity(uri).map(DocumentIdentity::id).orElse(null);
        DocumentId restored = step.restoreId;
        if (restored != null && !restored.equals(occupant) && identities.find(restored).isEmpty()
            && (occupant == null || !host.isOpen(occupant))) {
            if (occupant != null) {
                identities.dissociate(occupant);
                journal.push(new Compensation(step.targetUri, "restore identity at " + step.targetUri,
                    () -> identities.associate(occupant, uri)));
            }
            identities.associate(restored, uri);
            journal.push(new Compensation(step.targetUri, "forget restored identity of " + step.targetUri,
                () -> identities.dissociate(restored)));
        }
        boolean allocated = identities.findIdentity(uri).isEmpty();
        DocumentIdentity identity = identities.identify(uri);
        if (allocated) {
            journal.push(new Compensation(step.targetUri, "forget identity of " + step.targetUri,
                () -> identities.dissociate(identity.id())));
        }
        DocumentSnapshot snapshot;
        try {
            snapshot = ProjectDocumentSynchronizer.backingSnapshot(identity.id(), uri,
                nextVersion(identity.id()), step.target, step.content);
        } catch (CharacterCodingException exception) {
            throw new StepFailure(WorkspaceEditProblem.Kind.INVALID, step.targetUri, "Text file content is not UTF-8");
        }
        store.publishBacking(snapshot, null);
        step.id = identity.id();
        journal.push(new Compensation(step.targetUri, "unpublish " + step.targetUri,
            () -> store.state(identity.id()).map(DocumentState::backing)
                .ifPresent(backing -> store.removeBacking(identity.id(), backing.version()))));
    }

    private void unpublishDeleted(ResourceStep step, Deque<Compensation> journal) {
        // Another hard link to the deleted file still holds the document's content and identity.
        if (step.alias != null) {
            identities.forgetDeletedAlias(step.id, step.source);
            journal.push(new Compensation(step.sourceUri, "restore identity at " + step.sourceUri,
                () -> identities.associate(step.id, step.source)));
            store.relocate(step.id, DocumentUri.fromPath(step.alias));
            journal.push(new Compensation(step.sourceUri, "move " + step.id + " back to " + step.sourceUri,
                () -> store.relocate(step.id, step.sourceUri)));
            return;
        }
        store.removeBacking(step.id, step.state.backing().version());
        journal.push(new Compensation(step.sourceUri, "republish " + step.sourceUri,
            () -> store.publishBacking(withVersion(step.expected, nextVersion(step.id)), null)));
    }

    private DocumentId restorableIdentity(DocumentId id) {
        return id != null && store.state(id).isEmpty() && !host.isOpen(id) ? id : null;
    }

    private void relocate(ResourceStep step, Deque<Compensation> journal) {
        DocumentUri targetUri = DocumentUri.fromPath(step.target);
        identities.findIdentity(targetUri).map(DocumentIdentity::id)
            .filter(other -> !other.equals(step.id) && store.state(other).isEmpty())
            .ifPresent(other -> {
                identities.dissociate(other);
                journal.push(new Compensation(targetUri, "restore identity at " + targetUri,
                    () -> identities.associate(other, targetUri)));
            });
        // Journaled first and idempotent: relocation updates identity, store and editor in separate steps, and
        // an editor can only be rebound once its file is back at the source.
        journal.push(new Compensation(step.sourceUri, "move " + step.id + " back to " + step.sourceUri, () -> {
            moveBack(step);
            Optional<DocumentIdentity> moved = identities.findIdentity(targetUri)
                .filter(identity -> identity.id().equals(step.id));
            if (moved.isPresent()) {
                host.relocate(moved.get(), step.source);
            } else {
                store.relocate(step.id, step.sourceUri);
            }
        }));
        host.relocate(step.sourceIdentity, step.target);
    }

    private void moveBack(ResourceStep step) throws IOException {
        if (step.temporary != null) {
            files.move(step.temporary, step.source);
            step.temporary = null;
            return;
        }
        if (step.caseOnly ? hasName(step.source) : files.exists(step.source) && !files.exists(step.target))
            return;
        move(step.target, step.source, step);
    }

    /**
     * Moves a renamed file. A case-only move that fails part-way and cannot return the file to where it started
     * records the temporary location in {@code step} so that {@link #moveBack} can still restore it.
     */
    private void move(Path source, Path target, ResourceStep step) throws IOException {
        if (!step.caseOnly) {
            files.move(source, target);
            return;
        }
        // Case-insensitive filesystems treat a case-only move as a move onto the same file and skip it.
        Path temporary = source.resolveSibling(source.getFileName() + ".railroad-rename-" + UUID.randomUUID());
        files.move(source, temporary);
        try {
            files.move(temporary, target);
        } catch (IOException exception) {
            try {
                files.move(temporary, source);
            } catch (IOException restoreFailure) {
                step.temporary = temporary;
                exception.addSuppressed(restoreFailure);
                throw new IOException("Could not move " + temporary + " to " + target + " or back to " + source,
                    exception);
            }
            throw exception;
        }
    }

    private static boolean hasName(Path path) throws IOException {
        return path.toRealPath().getFileName().toString().equals(path.getFileName().toString());
    }

    private void createFile(ResourceStep step, Deque<Compensation> journal) throws StepFailure {
        var created = new boolean[1];
        // Journaled first: a failed write may leave a partial file that this edit created.
        journal.push(new Compensation(step.targetUri, "remove created " + step.target, () -> {
            if (created[0]) {
                deleteIfUnchanged(step.target, step.content);
            } else if (files.exists(step.target)) {
                files.delete(step.target);
            }
        }));
        try {
            files.createFile(step.target, step.content);
            created[0] = true;
        } catch (FileAlreadyExistsException exception) {
            journal.pop();
            throw new StepFailure(WorkspaceEditProblem.Kind.ALREADY_EXISTS, step.targetUri,
                "File was created by another process");
        } catch (IOException exception) {
            throw new StepFailure(WorkspaceEditProblem.Kind.IO_FAILURE, step.targetUri,
                exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private void createParents(ResourceStep step, Deque<Compensation> journal) throws StepFailure {
        Deque<Path> missing = new ArrayDeque<>();
        for (Path parent = step.target.getParent(); parent != null
            && !files.exists(parent); parent = parent.getParent()) {
            missing.push(parent);
        }
        for (Path directory : missing) {
            io(step.targetUri, () -> files.createDirectory(directory));
            step.createdDirectories.add(directory);
            journal.push(new Compensation(step.targetUri, "remove created directory " + directory,
                () -> files.delete(directory)));
        }
    }

    private void removeEmptyDirectories(ResourceStep step, Deque<Compensation> journal) {
        for (Path directory : step.cleanup.reversed()) {
            try {
                files.delete(directory);
                journal.push(new Compensation(step.sourceUri, "recreate directory " + directory,
                    () -> files.createDirectory(directory)));
            } catch (IOException exception) {
                Railroad.LOGGER.debug("Retaining directory {} after workspace edit", directory, exception);
                return;
            }
        }
    }

    private void deleteIfUnchanged(Path path, byte[] content) throws IOException {
        if (!Arrays.equals(files.read(path), content))
            throw new IOException("Created file changed before it could be removed");
        files.delete(path);
    }

    private WorkspaceEditResult rolledBack(WorkspaceEditProblem cause, Deque<Compensation> journal) {
        List<WorkspaceEditProblem> problems = new ArrayList<>();
        problems.add(cause);
        boolean consistent = true;
        while (!journal.isEmpty()) {
            Compensation compensation = journal.pop();
            try {
                compensation.action.run();
            } catch (Exception exception) {
                consistent = false;
                Railroad.LOGGER.error("Could not {} while rolling back a workspace edit", compensation.description,
                    exception);
                problems.add(new WorkspaceEditProblem(WorkspaceEditProblem.Kind.IO_FAILURE, -1, compensation.uri,
                    "Could not " + compensation.description + ": " + exception.getMessage()));
            }
        }
        return new WorkspaceEditResult(consistent
            ? WorkspaceEditResult.Status.ROLLED_BACK
            : WorkspaceEditResult.Status.INCONSISTENT, problems);
    }

    private void rememberReverted(Object change) {
        revertedChanges.addFirst(new WeakReference<>(change));
        revertedChanges.removeIf(reference -> reference.get() == null);
        while (revertedChanges.size() > HISTORY_LIMIT) {
            revertedChanges.removeLast();
        }
    }

    private void attach(TextEditorPane editor) {
        if (editor.getSharedUndoHistory() != this) {
            editor.setSharedUndoHistory(this);
        }
        attachedEditors.add(editor);
    }

    private Path workspacePath(Plan plan, int index, DocumentUri uri) {
        Optional<Path> path = uri.filePath();
        if (path.isEmpty()) {
            plan.problem(WorkspaceEditProblem.Kind.OUTSIDE_WORKSPACE, index, uri,
                "Only project files can be changed without an open editor");
            return null;
        }
        Path normalized = path.get().toAbsolutePath().normalize();
        if (!ProjectDocumentSynchronizer.isWorkspacePath(root, canonical(normalized))) {
            plan.problem(WorkspaceEditProblem.Kind.OUTSIDE_WORKSPACE, index, uri,
                "Location is outside the project or in a directory Railroad does not manage");
            return null;
        }
        return normalized;
    }

    private boolean diskMatches(Plan plan, int index, Path path, DocumentSnapshot expected) {
        try {
            if (matches(files.read(path), expected))
                return true;
            plan.problem(WorkspaceEditProblem.Kind.STALE, index, expected.uri(),
                "File changed on disk since it was last read");
        } catch (IOException exception) {
            plan.problem(WorkspaceEditProblem.Kind.IO_FAILURE, index, expected.uri(),
                "Could not read file: " + exception.getMessage());
        }
        return false;
    }

    private boolean willExist(Path path, Set<Path> vacated, Set<Path> occupied) {
        return occupied.contains(path) || !vacated.contains(path) && files.exists(path);
    }

    private boolean parentWritable(Path path) {
        for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
            if (files.exists(parent))
                return files.isDirectory(parent) && files.isWritable(parent);
        }
        return false;
    }

    private DocumentVersion nextVersion(DocumentId id) {
        return store.latestVersion(id).map(DocumentVersion::next).orElse(DocumentVersion.initial());
    }

    private static Path canonical(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path existing = normalized;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null)
            return normalized;
        try {
            return existing.toRealPath().resolve(existing.relativize(normalized)).normalize();
        } catch (IOException exception) {
            return normalized;
        }
    }

    private static boolean sameLocation(DocumentUri first, DocumentUri second) {
        if (first.equals(second))
            return true;
        Optional<Path> firstPath = first.filePath();
        Optional<Path> secondPath = second.filePath();
        return firstPath.isPresent() && secondPath.isPresent()
            && canonical(firstPath.get()).equals(canonical(secondPath.get()));
    }

    private static boolean readOnlyOrigin(DocumentSnapshot snapshot) {
        return switch (snapshot.origin().provenance()) {
            case GENERATED, DECOMPILED, ATTACHED_SOURCE -> true;
            case ORIGINAL, UNSPECIFIED -> false;
        };
    }

    private static boolean sameSnapshot(DocumentSnapshot first, DocumentSnapshot second) {
        return first.id().equals(second.id()) && first.version().equals(second.version())
            && first.uri().equals(second.uri()) && sameContent(first, second);
    }

    private static boolean sameContent(DocumentSnapshot first, DocumentSnapshot second) {
        if (first instanceof TextDocumentSnapshot firstText && second instanceof TextDocumentSnapshot secondText)
            return firstText.text().equals(secondText.text());
        if (first instanceof BinaryDocumentSnapshot firstBinary
            && second instanceof BinaryDocumentSnapshot secondBinary)
            return firstBinary.bytes().equals(secondBinary.bytes());
        return false;
    }

    private static boolean matches(byte[] bytes, DocumentSnapshot expected) {
        if (expected instanceof BinaryDocumentSnapshot binary)
            return binary.bytes().equals(ByteBuffer.wrap(bytes));
        var text = (TextDocumentSnapshot) expected;
        try {
            return text.encoding().newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().equals(text.text());
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static boolean contentEquals(DocumentSnapshot snapshot, byte[] content) {
        try {
            return Arrays.equals(contentBytes(snapshot), content);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] contentBytes(DocumentSnapshot snapshot) {
        return switch (snapshot) {
            case BinaryDocumentSnapshot binary -> binary.copyBytes();
            case TextDocumentSnapshot text -> {
                try {
                    yield encode(text.text(), text.encoding(), text.uri());
                } catch (StepFailure failure) {
                    throw new IllegalArgumentException(failure.problem.message());
                }
            }
        };
    }

    private static byte[] encode(String text, Charset encoding, DocumentUri uri) throws StepFailure {
        try {
            ByteBuffer buffer = encoding.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new StepFailure(WorkspaceEditProblem.Kind.INVALID, uri,
                "Text cannot be encoded as " + encoding.name());
        }
    }

    private static TextDocumentSnapshot withText(TextDocumentSnapshot base, String text, DocumentVersion version) {
        return new TextDocumentSnapshot(base.id(), base.uri(), version, base.languageId(), base.origin(), text,
            base.encoding());
    }

    private static DocumentSnapshot withVersion(DocumentSnapshot base, DocumentVersion version) {
        return switch (base) {
            case TextDocumentSnapshot text -> withText(text, text.text(), version);
            case BinaryDocumentSnapshot binary -> new BinaryDocumentSnapshot(binary.id(), binary.uri(), version,
                binary.languageId(), binary.origin(), binary.copyBytes());
        };
    }

    /**
     * Returns the text a recorded undo or redo should leave in a document, or {@code null} if the document changed
     * since. Opening an editor normalizes line endings, so a document whose only change is that normalization is
     * given the normalized target.
     */
    private static String replayTarget(String current, String expected, String target) {
        if (current.equals(expected))
            return target;
        return current.equals(normalizeLineEndings(expected)) ? normalizeLineEndings(target) : null;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<TextReplacement> minimalReplacement(String from, String to) {
        if (from.equals(to))
            return List.of();
        int prefix = 0;
        int limit = Math.min(from.length(), to.length());
        while (prefix < limit && from.charAt(prefix) == to.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < limit - prefix
            && from.charAt(from.length() - 1 - suffix) == to.charAt(to.length() - 1 - suffix)) {
            suffix++;
        }
        return List.of(new TextReplacement(new TextRange(prefix, from.length() - suffix),
            to.substring(prefix, to.length() - suffix)));
    }

    private static <T> T io(DocumentUri uri, IoSupplier<T> action) throws StepFailure {
        try {
            return action.get();
        } catch (IOException exception) {
            throw new StepFailure(WorkspaceEditProblem.Kind.IO_FAILURE, uri,
                exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static void io(DocumentUri uri, IoAction action) throws StepFailure {
        io(uri, () -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface CompensationAction {
        void run() throws Exception;
    }

    private record Compensation(DocumentUri uri, String description, CompensationAction action) {
    }

    private record Replacement(int start, int end, String text, String annotationId, int changeIndex) {
    }

    private record NativeStep(TextEditorPane editor, Object token, boolean redo) {
    }

    /**
     * State an undo or redo carries into planning: editor history steps to reuse, directories each change index
     * should remove, and the identity each recreated file should get back.
     */
    private record Replay(
        Map<DocumentId, NativeStep> natives,
        Map<Integer, List<Path>> cleanup,
        Map<Integer, DocumentId> identities
    ) {
        private static final Replay NONE = new Replay(Map.of(), Map.of(), Map.of());
    }

    private static final class StepFailure extends Exception {
        private final WorkspaceEditProblem problem;

        private StepFailure(WorkspaceEditProblem.Kind kind, DocumentUri uri, String message) {
            super(message, null, false, false);
            problem = new WorkspaceEditProblem(kind, -1, uri, message);
        }
    }

    private static final class Plan {
        private final WorkspaceEdit edit;
        private final List<TextTarget> texts = new ArrayList<>();
        private final List<ResourceStep> resources = new ArrayList<>();
        private final List<WorkspaceEditProblem> problems = new ArrayList<>();

        private Plan(WorkspaceEdit edit) {
            this.edit = edit;
        }

        private void problem(WorkspaceEditProblem.Kind kind, int index, DocumentUri uri, String message) {
            problems.add(new WorkspaceEditProblem(kind, index, uri, message));
        }

        private TextTarget target(DocumentId id) {
            return texts.stream().filter(target -> target.id.equals(id)).findFirst().orElse(null);
        }
    }

    private static final class TextTarget {
        private final DocumentId id;
        private final TextDocumentSnapshot expected;
        private final List<Replacement> replacements = new ArrayList<>();
        private Target kind;
        private TextEditorPane editor;
        private DocumentState state;
        private Path path;
        private String after;
        private NativeStep nativeStep;
        private TextDocumentSnapshot applied;
        private Object token;
        private boolean tokenOnRedo;
        private TextSelection selectionBefore;
        private TextSelection selectionAfter;

        private TextTarget(TextDocumentSnapshot expected) {
            this.id = expected.id();
            this.expected = expected;
        }

        private boolean changes() {
            return !after.equals(expected.text());
        }
    }

    private static final class ResourceStep {
        private final WorkspaceEditPreview.ResourcePreview.Kind kind;
        private final List<Path> createdDirectories = new ArrayList<>();
        private List<Path> cleanup = List.of();
        private DocumentId id;
        private DocumentSnapshot expected;
        private DocumentState state;
        private Path source;
        private DocumentUri sourceUri;
        private DocumentIdentity sourceIdentity;
        private Path target;
        private DocumentUri targetUri;
        private byte[] content;
        private boolean caseOnly;
        private Path temporary;
        private Path alias;
        private DocumentId restoreId;

        private ResourceStep(WorkspaceEditPreview.ResourcePreview.Kind kind) {
            this.kind = kind;
        }
    }

    private static final class TextRecord {
        private final DocumentId id;
        private final String before;
        private final String after;
        private TextSelection selectionBefore;
        private TextSelection selectionAfter;
        private WeakReference<TextEditorPane> editor = new WeakReference<>(null);
        private Object token;
        private boolean tokenOnRedo;

        private TextRecord(DocumentId id, String before, String after) {
            this.id = id;
            this.before = before;
            this.after = after;
        }

        private void update(TextTarget target) {
            editor = new WeakReference<>(target.editor);
            token = target.token;
            tokenOnRedo = target.tokenOnRedo;
        }
    }

    private record ResourceRecord(
        WorkspaceEditPreview.ResourcePreview.Kind kind,
        DocumentId id,
        DocumentUri source,
        DocumentUri target,
        byte[] content,
        List<Path> createdDirectories
    ) {
    }

    private static final class HistoryEntry {
        private final String label;
        private final List<TextRecord> texts = new ArrayList<>();
        private final List<ResourceRecord> resources = new ArrayList<>();
        private boolean undone;

        private HistoryEntry(String label) {
            this.label = label;
        }

        private static HistoryEntry from(String label, Plan plan) {
            var entry = new HistoryEntry(label);
            for (TextTarget target : plan.texts) {
                if (target.applied == null || target.applied.text().equals(target.expected.text()))
                    continue;
                var textRecord = new TextRecord(target.id, target.expected.text(), target.applied.text());
                textRecord.update(target);
                textRecord.selectionBefore = target.selectionBefore;
                textRecord.selectionAfter = target.selectionAfter;
                entry.texts.add(textRecord);
            }
            for (ResourceStep step : plan.resources) {
                entry.resources.add(new ResourceRecord(step.kind, step.id, step.sourceUri,
                    step.target == null ? null : DocumentUri.fromPath(step.target), step.content,
                    List.copyOf(step.createdDirectories)));
            }
            return entry;
        }

        private void update(Plan plan, boolean undo) {
            undone = undo;
            for (TextTarget target : plan.texts) {
                texts.stream().filter(candidate -> candidate.id.equals(target.id)).findFirst().orElseThrow()
                    .update(target);
            }
            int count = resources.size();
            for (int index = 0; index < count; index++) {
                int original = undo ? count - 1 - index : index;
                ResourceRecord record = resources.get(original);
                ResourceStep step = plan.resources.get(index);
                resources.set(original, new ResourceRecord(record.kind, step.id == null ? record.id : step.id,
                    record.source, record.target, record.content, undo || step.createdDirectories.isEmpty()
                        ? record.createdDirectories
                        : List.copyOf(step.createdDirectories)));
            }
        }
    }
}
