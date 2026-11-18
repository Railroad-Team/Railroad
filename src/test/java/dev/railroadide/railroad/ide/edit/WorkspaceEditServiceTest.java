package dev.railroadide.railroad.ide.edit;

import dev.railroadide.railroad.ide.ProjectDocumentSynchronizer;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditProblem.Kind;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult.Status;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactfx.Subscription;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceEditServiceTest {
    @TempDir
    public Path root;
    @TempDir
    public Path outside;
    private DocumentStore store;
    private DocumentIdentityRegistry identities;
    private FaultyFiles files;
    private TestHost host;
    private WorkspaceEditService service;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            LanguageSupports.initialize();
            return null;
        });
    }

    @BeforeEach
    public void createService() {
        store = new DefaultDocumentStore("workspace-edits", new WorkspaceConfigurationSnapshot(0, Set.of()));
        identities = new DocumentIdentityRegistry();
        files = new FaultyFiles();
        host = new TestHost();
        service = new WorkspaceEditService(root, store, identities, host, files);
    }

    @AfterEach
    public void closeEditors() throws Exception {
        onFx(() -> {
            service.close();
            for (TextEditorPane editor : host.editors.values()) {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            }
            return null;
        });
    }

    @Test
    public void appliesUnorderedNonOverlappingEditsToOneClosedDocument() throws Exception {
        TextDocumentSnapshot document = disk("Example.txt", "alpha beta gamma");
        long revision = store.capture().revision();

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Edit")
            .replace(document, replacement(11, 16, "GAMMA"), replacement(0, 5, "ALPHA"), replacement(5, 5, "!"))
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals("ALPHA! beta GAMMA", Files.readString(root.resolve("Example.txt")));
        var published = (TextDocumentSnapshot) store.current(document.id()).orElseThrow();
        assertEquals("ALPHA! beta GAMMA", published.text());
        assertTrue(published.version().isAfter(document.version()));
        assertNull(store.state(document.id()).orElseThrow().overlay());
        assertTrue(store.capture().revision() > revision);
    }

    @Test
    public void appliesOneEditAcrossEditorDraftAndClosedDocuments() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor text");
        onFx(() -> {
            editor.insertText(0, "unsaved ");
            return null;
        });
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot draft = draft("Draft.txt", "saved draft", "unsaved draft");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed text");
        var notifications = new AtomicInteger();
        store.onChange(notifications::incrementAndGet);

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Rename text")
            .replace(editorSnapshot, replacement(8, 14, "EDITOR"))
            .replace(draft, replacement(8, 13, "DRAFT"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals("unsaved EDITOR text", onFx(editor::getText));
        assertEquals("editor text", Files.readString(root.resolve("Editor.txt")));
        assertEquals("unsaved DRAFT", ((TextDocumentSnapshot) store.current(draft.id()).orElseThrow()).text());
        assertNotNull(store.state(draft.id()).orElseThrow().overlay());
        assertEquals("saved draft", Files.readString(root.resolve("Draft.txt")));
        assertEquals("CLOSED text", Files.readString(root.resolve("Closed.txt")));
        assertTrue(notifications.get() >= 3);
        assertTrue(onFx(service::canUndo));
    }

    @Test
    public void rejectsStaleEditorSnapshotWithoutChangingOtherDocuments() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        WorkspaceEdit edit = WorkspaceEdit.builder("Edit")
            .replace(closed, replacement(0, 6, "CHANGED"))
            .replace(editorSnapshot, replacement(0, 6, "CHANGED"))
            .build();
        onFx(() -> {
            editor.appendText(" typed");
            return null;
        });

        WorkspaceEditResult result = apply(edit);

        assertEquals(Status.REJECTED, result.status());
        assertEquals(List.of(Kind.STALE), kinds(result));
        assertEquals(1, result.problems().getFirst().changeIndex());
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));
        assertSame(closed, store.current(closed.id()).orElseThrow());
        assertEquals("editor typed", onFx(editor::getText));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void rejectsUnobservedDiskChangesAndMissingFiles() throws Exception {
        TextDocumentSnapshot changed = disk("Changed.txt", "before");
        TextDocumentSnapshot missing = disk("Missing.txt", "before");
        Files.writeString(root.resolve("Changed.txt"), "external");
        Files.delete(root.resolve("Missing.txt"));

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Edit")
            .replace(changed, replacement(0, 1, "B"))
            .replace(missing, replacement(0, 1, "B"))
            .build());

        assertEquals(List.of(Kind.STALE, Kind.MISSING), kinds(result));
        assertEquals("external", Files.readString(root.resolve("Changed.txt")));
    }

    @Test
    public void rejectsOverlapsDuplicateStartsOutOfBoundsAndMixedRevisions() throws Exception {
        TextDocumentSnapshot document = disk("Overlaps.txt", "0123456789");
        assertEquals(List.of(Kind.OVERLAP), kinds(apply(WorkspaceEdit.builder("Overlap")
            .replace(document, replacement(0, 5, "a"), replacement(4, 6, "b")).build())));
        assertEquals(List.of(Kind.OVERLAP), kinds(apply(WorkspaceEdit.builder("Same start")
            .replace(document, replacement(3, 3, "a"), replacement(3, 3, "b")).build())));
        assertEquals(List.of(Kind.OVERLAP), kinds(apply(WorkspaceEdit.builder("Bounds")
            .replace(document, replacement(8, 11, "a")).build())));
        assertEquals(List.of(Kind.OVERLAP), kinds(apply(WorkspaceEdit.builder("Across changes")
            .replace(document, replacement(0, 3, "a"))
            .replace(document, replacement(2, 4, "b"))
            .build())));
        var older = new TextDocumentSnapshot(document.id(), document.uri(), new DocumentVersion(99), "plaintext",
            document.text(), StandardCharsets.UTF_8);
        assertEquals(List.of(Kind.CONFLICT), kinds(apply(WorkspaceEdit.builder("Mixed")
            .replace(document, replacement(0, 1, "a"))
            .replace(older, replacement(5, 6, "b"))
            .build())));

        WorkspaceEditResult adjacent = apply(WorkspaceEdit.builder("Adjacent")
            .replace(document, replacement(0, 2, "ab"), replacement(2, 4, "cd")).build());
        assertEquals(Status.APPLIED, adjacent.status(), adjacent.problems()::toString);
        assertEquals("abcd456789", Files.readString(root.resolve("Overlaps.txt")));
    }

    @Test
    public void rejectsReadOnlyGeneratedAndExternalTargets() throws Exception {
        TextEditorPane editor = openEditor("ReadOnly.txt", "locked");
        onFx(() -> {
            editor.setEditable(false);
            return null;
        });
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot readOnlyFile = disk("ReadOnlyFile.txt", "locked");
        assertTrue(root.resolve("ReadOnlyFile.txt").toFile().setReadOnly());
        Path generatedPath = root.resolve("Generated.txt");
        Files.writeString(generatedPath, "generated");
        DocumentId generatedId = identities.getOrCreate(generatedPath);
        DocumentUri generatedUri = DocumentUri.fromPath(generatedPath);
        var generated = new TextDocumentSnapshot(generatedId, generatedUri, DocumentVersion.initial(), "plaintext",
            new DocumentOrigin(BackingKind.FILE, ProvenanceKind.GENERATED), "generated", StandardCharsets.UTF_8);
        store.publishBacking(generated);
        Path external = outside.resolve("External.txt");
        Files.writeString(external, "external");
        TextDocumentSnapshot externalSnapshot = publish(external, "external");
        Path ignored = root.resolve(".gradle").resolve("Ignored.txt");
        Files.createDirectories(ignored.getParent());
        Files.writeString(ignored, "ignored");
        TextDocumentSnapshot ignoredSnapshot = publish(ignored, "ignored");

        try {
            WorkspaceEditResult result = apply(WorkspaceEdit.builder("Edit")
                .replace(editorSnapshot, replacement(0, 1, "x"))
                .replace(readOnlyFile, replacement(0, 1, "x"))
                .replace(generated, replacement(0, 1, "x"))
                .replace(externalSnapshot, replacement(0, 1, "x"))
                .replace(ignoredSnapshot, replacement(0, 1, "x"))
                .build());

            assertEquals(List.of(Kind.READ_ONLY, Kind.READ_ONLY, Kind.READ_ONLY, Kind.OUTSIDE_WORKSPACE,
                Kind.OUTSIDE_WORKSPACE), kinds(result));
            assertEquals("locked", onFx(editor::getText));
            assertEquals("external", Files.readString(external));
        } finally {
            root.resolve("ReadOnlyFile.txt").toFile().setWritable(true);
        }
    }

    @Test
    public void previewReportsTextAndFileChangesWithoutMutation() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "hello world");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot closed = disk("Closed.txt", "abc");
        DocumentUri created = DocumentUri.fromPath(root.resolve("New.txt"));
        DocumentUri moved = DocumentUri.fromPath(root.resolve("Moved.txt"));
        long revision = store.capture().revision();

        WorkspaceEditPreview preview = onFx(() -> service.preview(WorkspaceEdit.builder("Preview")
            .annotation("rename", new ChangeAnnotation("Rename", "Renames a word", true))
            .change(new WorkspaceChange.TextEdit(editorSnapshot,
                List.of(replacement(6, 11, "there"), replacement(0, 5, "hi")), "rename"))
            .replace(closed, replacement(1, 2, "BBB"))
            .createText(created, "new")
            .rename(closed, moved)
            .build()));

        assertTrue(preview.applicable(), preview.problems()::toString);
        assertEquals(2, preview.documents().size());
        var editorPreview = preview.documents().getFirst();
        assertEquals(WorkspaceEditPreview.Target.EDITOR, editorPreview.target());
        assertEquals("hello world", editorPreview.before());
        assertEquals("hi there", editorPreview.after());
        assertEquals(new TextRange(0, 5), editorPreview.changes().get(0).before());
        assertEquals(new TextRange(0, 2), editorPreview.changes().get(0).after());
        assertEquals(new TextRange(6, 11), editorPreview.changes().get(1).before());
        assertEquals(new TextRange(3, 8), editorPreview.changes().get(1).after());
        assertEquals("rename", editorPreview.changes().get(1).annotationId());
        var closedPreview = preview.documents().get(1);
        assertEquals(WorkspaceEditPreview.Target.DISK, closedPreview.target());
        assertEquals("aBBBc", closedPreview.after());
        assertEquals(List.of(WorkspaceEditPreview.ResourcePreview.Kind.CREATE,
            WorkspaceEditPreview.ResourcePreview.Kind.RENAME),
            preview.resources().stream().map(WorkspaceEditPreview.ResourcePreview::kind).toList());
        assertEquals(revision, store.capture().revision());
        assertEquals("hello world", onFx(editor::getText));
        assertFalse(Files.exists(root.resolve("New.txt")));
        assertFalse(onFx(service::canUndo));

        WorkspaceEditPreview invalid = onFx(() -> service.preview(WorkspaceEdit.builder("Invalid")
            .replace(closed, replacement(0, 2, "x"), replacement(1, 3, "y")).build()));
        assertFalse(invalid.applicable());
        assertEquals(Kind.OVERLAP, invalid.problems().getFirst().kind());
    }

    @Test
    public void createsDeletesAndRenamesClosedFiles() throws Exception {
        TextDocumentSnapshot deleted = disk("Delete.txt", "delete me");
        TextDocumentSnapshot moved = disk("Move.txt", "move me");
        DocumentUri created = DocumentUri.fromPath(root.resolve("a").resolve("b").resolve("Created.txt"));
        DocumentUri destination = DocumentUri.fromPath(root.resolve("moved").resolve("Moved.txt"));

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Files")
            .replace(moved, replacement(0, 4, "MOVE"))
            .createText(created, "created")
            .delete(deleted)
            .rename(moved, destination)
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals("created", Files.readString(root.resolve("a/b/Created.txt")));
        DocumentId createdId = identities.findIdentity(created).orElseThrow().id();
        assertEquals("created", ((TextDocumentSnapshot) store.current(createdId).orElseThrow()).text());
        assertFalse(Files.exists(root.resolve("Delete.txt")));
        assertTrue(store.state(deleted.id()).isEmpty());
        assertFalse(Files.exists(root.resolve("Move.txt")));
        assertEquals("MOVE me", Files.readString(root.resolve("moved/Moved.txt")));
        var relocated = (TextDocumentSnapshot) store.current(moved.id()).orElseThrow();
        assertEquals(destination, relocated.uri());
        assertEquals("MOVE me", relocated.text());
        assertEquals(moved.id(), identities.findIdentity(destination).orElseThrow().id());
    }

    @Test
    public void renamesAnOpenEditorAfterApplyingItsTextEdit() throws Exception {
        TextEditorPane editor = openEditor("Old.txt", "class Old {}");
        TextDocumentSnapshot snapshot = onFx(() -> editor.textEditorAccess().snapshot());
        DocumentUri destination = DocumentUri.fromPath(root.resolve("New.txt"));

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Rename class")
            .replace(snapshot, replacement(6, 9, "New"))
            .rename(snapshot, destination)
            .select(snapshot.id(), new TextSelection(6, 9))
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals(root.resolve("New.txt").toAbsolutePath().normalize(), editor.getFilePath());
        assertEquals("class New {}", onFx(editor::getText));
        assertEquals(new TextSelection(6, 9), onFx(() -> editor.textEditorAccess().selection()));
        assertTrue(Files.exists(root.resolve("New.txt")));
        assertFalse(Files.exists(root.resolve("Old.txt")));
        assertEquals(destination, store.current(snapshot.id()).orElseThrow().uri());
        assertEquals(snapshot.id(), identities.findIdentity(destination).orElseThrow().id());
    }

    @Test
    public void rejectsContradictoryAndUnsafeResourceOperations() throws Exception {
        TextDocumentSnapshot document = disk("Document.txt", "text");
        TextDocumentSnapshot other = disk("Other.txt", "other");
        TextEditorPane editor = openEditor("Open.txt", "open");
        TextDocumentSnapshot open = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot draft = draft("Draft.txt", "saved", "unsaved");
        DocumentUri existing = other.uri();
        DocumentUri fresh = DocumentUri.fromPath(root.resolve("Fresh.txt"));

        assertEquals(List.of(Kind.CONFLICT), kinds(apply(WorkspaceEdit.builder("Edit deleted")
            .replace(document, replacement(0, 1, "x")).delete(document).build())));
        assertEquals(List.of(Kind.ALREADY_EXISTS), kinds(apply(WorkspaceEdit.builder("Create existing")
            .createText(existing, "x").build())));
        assertEquals(List.of(Kind.ALREADY_EXISTS), kinds(apply(WorkspaceEdit.builder("Create twice")
            .createText(fresh, "x").createText(fresh, "y").build())));
        assertEquals(List.of(Kind.ALREADY_EXISTS), kinds(apply(WorkspaceEdit.builder("Move onto")
            .rename(document, existing).build())));
        assertEquals(List.of(Kind.CONFLICT), kinds(apply(WorkspaceEdit.builder("Move twice")
            .rename(document, fresh).rename(document, DocumentUri.fromPath(root.resolve("Again.txt"))).build())));
        assertEquals(List.of(Kind.IN_USE), kinds(apply(WorkspaceEdit.builder("Delete open")
            .delete(open).build())));
        assertEquals(List.of(Kind.IN_USE), kinds(apply(WorkspaceEdit.builder("Delete draft")
            .delete(draft).build())));
        assertEquals(List.of(Kind.CONFLICT), kinds(apply(WorkspaceEdit.builder("Delete after move")
            .rename(other, fresh).delete(other).build())));

        WorkspaceEditResult swap = apply(WorkspaceEdit.builder("Delete then reuse")
            .delete(document).createText(document.uri(), "replacement").build());
        assertEquals(Status.APPLIED, swap.status(), swap.problems()::toString);
        assertEquals("replacement", Files.readString(root.resolve("Document.txt")));
        assertTrue(Files.exists(root.resolve("Other.txt")));
        assertFalse(Files.exists(root.resolve("Fresh.txt")));
    }

    @Test
    public void rejectsStaleDeletesAndRenames() throws Exception {
        TextDocumentSnapshot deleted = disk("Delete.txt", "before");
        TextDocumentSnapshot moved = disk("Move.txt", "before");
        Files.writeString(root.resolve("Delete.txt"), "after");
        TextDocumentSnapshot newer = republish(moved, "newer");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Stale")
            .delete(deleted)
            .rename(moved, DocumentUri.fromPath(root.resolve("Moved.txt")))
            .build());

        assertEquals(List.of(Kind.STALE, Kind.STALE), kinds(result));
        assertTrue(Files.exists(root.resolve("Delete.txt")));
        assertSame(newer, store.current(moved.id()).orElseThrow());
    }

    @Test
    public void rollsBackFilesystemStepsWhenALaterFileOperationFails() throws Exception {
        TextDocumentSnapshot written = disk("Written.txt", "original");
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot deleted = disk("Deleted.txt", "deleted");
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        long revision = store.capture().revision();
        files.failure = (operation, path) -> operation.equals("move");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Fails")
            .replace(written, replacement(0, 8, "CHANGED"))
            .replace(editorSnapshot, replacement(0, 6, "CHANGED"))
            .createText(DocumentUri.fromPath(root.resolve("nested/dir/Created.txt")), "created")
            .delete(deleted)
            .rename(moved, DocumentUri.fromPath(root.resolve("Destination.txt")))
            .build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals(Kind.IO_FAILURE, result.problems().getFirst().kind());
        assertEquals(1, result.problems().size());
        assertEquals("original", Files.readString(root.resolve("Written.txt")));
        assertFalse(Files.exists(root.resolve("nested")));
        assertEquals("deleted", Files.readString(root.resolve("Deleted.txt")));
        assertEquals("moved", Files.readString(root.resolve("Moved.txt")));
        assertFalse(Files.exists(root.resolve("Destination.txt")));
        assertEquals("editor", onFx(editor::getText));
        assertSame(written, store.current(written.id()).orElseThrow());
        assertSame(deleted, store.current(deleted.id()).orElseThrow());
        assertEquals(revision, store.capture().revision());
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void rollsBackPublishedEditorStoreAndFileChangesWhenPublicationFails() throws Exception {
        TextDocumentSnapshot written = disk("Written.txt", "original");
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        host.failRelocationAfter = 0;

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Fails late")
            .replace(written, replacement(0, 8, "CHANGED"))
            .replace(editorSnapshot, replacement(0, 6, "CHANGED"))
            .rename(moved, DocumentUri.fromPath(root.resolve("Destination.txt")))
            .build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals("original", Files.readString(root.resolve("Written.txt")));
        assertEquals("original", ((TextDocumentSnapshot) store.current(written.id()).orElseThrow()).text());
        assertEquals("editor", onFx(editor::getText));
        assertEquals("moved", Files.readString(root.resolve("Moved.txt")));
        assertFalse(Files.exists(root.resolve("Destination.txt")));
        assertEquals(moved.uri(), store.current(moved.id()).orElseThrow().uri());
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void reportsLocationsThatRollbackCouldNotRestore() throws Exception {
        TextDocumentSnapshot written = disk("Written.txt", "original");
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        var overwrites = new AtomicInteger();
        files.failure = (operation, path) -> operation.equals("move")
            || operation.equals("overwrite") && overwrites.incrementAndGet() > 1;

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Fails twice")
            .replace(written, replacement(0, 8, "CHANGED"))
            .rename(moved, DocumentUri.fromPath(root.resolve("Destination.txt")))
            .build());

        assertEquals(Status.INCONSISTENT, result.status());
        assertEquals(2, result.problems().size());
        assertEquals(written.uri(), result.problems().get(1).uri());
        assertEquals("CHANGED", Files.readString(root.resolve("Written.txt")));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void rollbackNeverOverwritesAFileChangedAfterTheEditWroteIt() throws Exception {
        TextDocumentSnapshot written = disk("Written.txt", "original");
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        files.failure = (operation, path) -> {
            if (!operation.equals("move"))
                return false;
            try {
                Files.writeString(root.resolve("Written.txt"), "concurrent");
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return true;
        };

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Concurrent")
            .replace(written, replacement(0, 8, "CHANGED"))
            .rename(moved, DocumentUri.fromPath(root.resolve("Destination.txt")))
            .build());

        assertEquals(Status.INCONSISTENT, result.status());
        assertEquals("concurrent", Files.readString(root.resolve("Written.txt")));
    }

    @Test
    public void undoAndRedoSpanEditorsFilesAndFileOperationsAsOneStep() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor text");
        onFx(() -> {
            editor.selectRange(1, 3);
            return null;
        });
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot closed = disk("Closed.txt", "closed text");
        TextDocumentSnapshot moved = disk("Move.txt", "moved");
        DocumentUri created = DocumentUri.fromPath(root.resolve("pkg/Created.txt"));
        DocumentUri destination = DocumentUri.fromPath(root.resolve("dest/Moved.txt"));

        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Refactor")
            .replace(editorSnapshot, replacement(0, 6, "EDITOR"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .createText(created, "created")
            .rename(moved, destination)
            .select(editorSnapshot.id(), new TextSelection(0, 6))
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);

        WorkspaceEditResult undone = onFx(service::undo);

        assertEquals(Status.APPLIED, undone.status(), undone.problems()::toString);
        assertEquals("editor text", onFx(editor::getText));
        assertEquals(new TextSelection(1, 3), onFx(() -> editor.textEditorAccess().selection()));
        assertTrue(onFx(() -> editor.getUndoManager().isRedoAvailable()));
        assertEquals("closed text", Files.readString(root.resolve("Closed.txt")));
        assertEquals("closed text", ((TextDocumentSnapshot) store.current(closed.id()).orElseThrow()).text());
        assertFalse(Files.exists(root.resolve("pkg")));
        assertEquals("moved", Files.readString(root.resolve("Move.txt")));
        assertFalse(Files.exists(root.resolve("dest")));
        assertEquals(moved.uri(), store.current(moved.id()).orElseThrow().uri());
        assertFalse(onFx(service::canUndo));
        assertTrue(onFx(service::canRedo));

        WorkspaceEditResult redone = onFx(service::redo);

        assertEquals(Status.APPLIED, redone.status(), redone.problems()::toString);
        assertEquals("EDITOR text", onFx(editor::getText));
        assertEquals(new TextSelection(0, 6), onFx(() -> editor.textEditorAccess().selection()));
        assertEquals("CLOSED text", Files.readString(root.resolve("Closed.txt")));
        assertEquals("created", Files.readString(root.resolve("pkg/Created.txt")));
        assertEquals("moved", Files.readString(root.resolve("dest/Moved.txt")));
        assertEquals(destination, store.current(moved.id()).orElseThrow().uri());
        assertTrue(onFx(service::canUndo));
    }

    @Test
    public void editorUndoAndRedoCommandsRouteToTheWholeWorkspaceEdit() throws Exception {
        TextEditorPane first = openEditor("First.txt", "first");
        TextEditorPane second = openEditor("Second.txt", "second");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Edit")
            .replace(onFx(() -> first.textEditorAccess().snapshot()), replacement(0, 5, "FIRST"))
            .replace(onFx(() -> second.textEditorAccess().snapshot()), replacement(0, 6, "SECOND"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);

        onFx(() -> {
            second.undo();
            return null;
        });

        assertEquals("first", onFx(first::getText));
        assertEquals("second", onFx(second::getText));
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));

        onFx(() -> {
            first.redo();
            return null;
        });

        assertEquals("FIRST", onFx(first::getText));
        assertEquals("SECOND", onFx(second::getText));
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));
    }

    @Test
    public void laterTypingIsUndoneLocallyBeforeTheWorkspaceEdit() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        apply(WorkspaceEdit.builder("Edit")
            .replace(onFx(() -> editor.textEditorAccess().snapshot()), replacement(0, 6, "EDITOR"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());
        onFx(() -> {
            editor.appendText(" typed");
            editor.getUndoManager().preventMerge();
            return null;
        });

        onFx(() -> {
            editor.undo();
            return null;
        });
        assertEquals("EDITOR", onFx(editor::getText));
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));

        onFx(() -> {
            editor.undo();
            return null;
        });
        assertEquals("editor", onFx(editor::getText));
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));
    }

    @Test
    public void refusedSharedUndoChangesNothingThenFallsBackToLocalUndo() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        apply(WorkspaceEdit.builder("Edit")
            .replace(onFx(() -> editor.textEditorAccess().snapshot()), replacement(0, 6, "EDITOR"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());
        Files.writeString(root.resolve("Closed.txt"), "CLOSED!");
        republish((TextDocumentSnapshot) store.current(closed.id()).orElseThrow(), "CLOSED!");

        onFx(() -> {
            editor.undo();
            return null;
        });
        assertEquals("EDITOR", onFx(editor::getText));
        assertEquals("CLOSED!", Files.readString(root.resolve("Closed.txt")));

        onFx(() -> {
            editor.undo();
            return null;
        });
        assertEquals("editor", onFx(editor::getText));
        assertEquals("CLOSED!", Files.readString(root.resolve("Closed.txt")));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void undoOfCreatedFileIsRefusedAfterItChangesOrOpens() throws Exception {
        DocumentUri created = DocumentUri.fromPath(root.resolve("Created.txt"));
        apply(WorkspaceEdit.builder("Create").createText(created, "created").build());
        DocumentId id = identities.findIdentity(created).orElseThrow().id();
        republish((TextDocumentSnapshot) store.current(id).orElseThrow(), "changed");

        WorkspaceEditResult result = onFx(service::undo);

        assertEquals(Status.REJECTED, result.status());
        assertEquals(Kind.STALE, result.problems().getFirst().kind());
        assertTrue(Files.exists(root.resolve("Created.txt")));
    }

    @Test
    public void binaryDocumentsCanBeCreatedMovedAndDeletedWithUndo() throws Exception {
        byte[] bytes = {0, 1, 2, (byte) 0xff};
        Path image = root.resolve("image.png");
        Files.write(image, bytes);
        DocumentId id = identities.getOrCreate(image);
        DocumentSnapshot binary = ProjectDocumentSynchronizer.backingSnapshot(id, DocumentUri.fromPath(image),
            DocumentVersion.initial(), image, bytes);
        assertInstanceOf(BinaryDocumentSnapshot.class, binary);
        store.publishBacking(binary);
        DocumentUri copy = DocumentUri.fromPath(root.resolve("copy.png"));
        DocumentUri moved = DocumentUri.fromPath(root.resolve("moved.png"));

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Binary")
            .change(new WorkspaceChange.CreateFile(copy, bytes, null))
            .rename(binary, moved)
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("copy.png")));
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("moved.png")));
        DocumentSnapshot relocated = store.current(id).orElseThrow();
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Delete").delete(relocated).build()).status());
        assertFalse(Files.exists(root.resolve("moved.png")));

        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("moved.png")));
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertArrayEquals(bytes, Files.readAllBytes(image));
        assertFalse(Files.exists(root.resolve("copy.png")));
    }

    @Test
    public void rejectsInvalidSelectionsAndOffThreadCalls() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "abc");
        TextDocumentSnapshot snapshot = onFx(() -> editor.textEditorAccess().snapshot());

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Selection")
            .replace(snapshot, replacement(0, 3, "x"))
            .select(snapshot.id(), new TextSelection(0, 2))
            .build());

        assertEquals(List.of(Kind.INVALID), kinds(result));
        assertEquals("abc", onFx(editor::getText));
        assertThrows(IllegalStateException.class,
            () -> service.apply(WorkspaceEdit.builder("Off thread").replace(snapshot).build()));
    }

    @Test
    public void restoresAFileThatAFailedWriteLeftTruncated() throws Exception {
        TextDocumentSnapshot written = disk("Written.txt", "original content");
        files.failure = (operation, path) -> {
            if (!operation.equals("overwrite") || !path.endsWith("Written.txt"))
                return false;
            try {
                Files.writeString(path, "CHA");
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            files.failure = (_, _) -> false;
            return true;
        };

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Partial write")
            .replace(written, replacement(0, 8, "CHANGED")).build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals("original content", Files.readString(root.resolve("Written.txt")));
        assertSame(written, store.current(written.id()).orElseThrow());
    }

    @Test
    public void rollsBackAnOpenEditorRenameWhenALaterRelocationFails() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        host.failRelocationAfter = 1;

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Two moves")
            .replace(editorSnapshot, replacement(0, 6, "EDITOR"))
            .rename(editorSnapshot, DocumentUri.fromPath(root.resolve("EditorMoved.txt")))
            .rename(closed, DocumentUri.fromPath(root.resolve("ClosedMoved.txt")))
            .build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals(root.resolve("Editor.txt").toAbsolutePath().normalize(), editor.getFilePath());
        assertEquals("editor", onFx(editor::getText));
        assertEquals(editorSnapshot.uri(), store.current(editorSnapshot.id()).orElseThrow().uri());
        assertEquals(editorSnapshot.id(), identities.findIdentity(editorSnapshot.uri()).orElseThrow().id());
        assertTrue(Files.exists(root.resolve("Editor.txt")));
        assertTrue(Files.exists(root.resolve("Closed.txt")));
        assertFalse(Files.exists(root.resolve("EditorMoved.txt")));
        assertFalse(Files.exists(root.resolve("ClosedMoved.txt")));
    }

    @Test
    public void reusesALocationFreedInTheSameEditAndRestoresItsIdentityOnRollback() throws Exception {
        TextDocumentSnapshot replaced = disk("Target.txt", "replaced");
        TextDocumentSnapshot moved = disk("Source.txt", "moved");
        host.failRelocationAfter = 0;

        WorkspaceEditResult failed = apply(WorkspaceEdit.builder("Replace")
            .delete(replaced).rename(moved, replaced.uri()).build());

        assertEquals(Status.ROLLED_BACK, failed.status(), failed.problems()::toString);
        assertEquals("replaced", Files.readString(root.resolve("Target.txt")));
        assertEquals(replaced.id(), identities.findIdentity(replaced.uri()).orElseThrow().id());
        assertEquals("replaced", ((TextDocumentSnapshot) store.current(replaced.id()).orElseThrow()).text());

        host.failRelocationAfter = Integer.MAX_VALUE;
        DocumentSnapshot restored = store.current(replaced.id()).orElseThrow();
        assertTrue(restored.version().isAfter(replaced.version()));
        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Replace")
            .delete(restored).rename(store.current(moved.id()).orElseThrow(), replaced.uri()).build());

        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        assertEquals("moved", Files.readString(root.resolve("Target.txt")));
        assertEquals(moved.id(), identities.findIdentity(replaced.uri()).orElseThrow().id());
        assertTrue(store.state(replaced.id()).isEmpty());
    }

    @Test
    public void staleHistoryEntriesAreDroppedInsteadOfBlockingUndo() throws Exception {
        TextDocumentSnapshot first = disk("First.txt", "first");
        TextDocumentSnapshot second = disk("Second.txt", "second");
        apply(WorkspaceEdit.builder("First").replace(first, replacement(0, 5, "FIRST")).build());
        apply(WorkspaceEdit.builder("Second").replace(second, replacement(0, 6, "SECOND")).build());
        Files.writeString(root.resolve("Second.txt"), "external");
        republish((TextDocumentSnapshot) store.current(second.id()).orElseThrow(), "external");

        assertEquals(Status.REJECTED, onFx(service::undo).status());
        WorkspaceEditResult older = onFx(service::undo);

        assertEquals(Status.APPLIED, older.status(), older.problems()::toString);
        assertEquals("first", Files.readString(root.resolve("First.txt")));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void emptyAndIdentityEditsSucceedWithoutNewRevisionsOrHistory() throws Exception {
        TextDocumentSnapshot closed = disk("Closed.txt", "same");
        TextEditorPane editor = openEditor("Editor.txt", "same");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        long revision = store.capture().revision();

        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Empty").build()).status());
        WorkspaceEditResult identity = apply(WorkspaceEdit.builder("Identity")
            .replace(closed, replacement(0, 4, "same"))
            .replace(editorSnapshot, replacement(1, 2, "a"))
            .build());

        assertEquals(Status.APPLIED, identity.status(), identity.problems()::toString);
        assertEquals(revision, store.capture().revision());
        assertSame(closed, store.current(closed.id()).orElseThrow());
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void undoSpansDocumentsAfterOpeningACrlfFileThatTheEditorNormalizes() throws Exception {
        TextDocumentSnapshot windows = disk("Windows.txt", "first\r\nsecond\r\n");
        TextDocumentSnapshot other = disk("Other.txt", "other");
        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Edit")
            .replace(windows, replacement(0, 5, "FIRST"))
            .replace(other, replacement(0, 5, "OTHER"))
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        TextEditorPane editor = openDiscovered(root.resolve("Windows.txt"));
        assertEquals("FIRST\nsecond\n", onFx(editor::getText));

        WorkspaceEditResult undone = onFx(service::undo);

        assertEquals(Status.APPLIED, undone.status(), undone.problems()::toString);
        assertEquals("first\nsecond\n", onFx(editor::getText));
        assertEquals("other", Files.readString(root.resolve("Other.txt")));
        WorkspaceEditResult redone = onFx(service::redo);
        assertEquals(Status.APPLIED, redone.status(), redone.problems()::toString);
        assertEquals("FIRST\nsecond\n", onFx(editor::getText));
        assertEquals("OTHER", Files.readString(root.resolve("Other.txt")));
    }

    @Test
    public void undoRestoresADeletedBinaryFileWithAnUnrecognisedExtension() throws Exception {
        byte[] bytes = {0, 1, 2, (byte) 0xff};
        Path payload = root.resolve("payload.unrecognised");
        Files.write(payload, bytes);
        DocumentId id = identities.getOrCreate(payload);
        DocumentSnapshot binary = ProjectDocumentSynchronizer.backingSnapshot(id, DocumentUri.fromPath(payload),
            DocumentVersion.initial(), payload, bytes);
        assertInstanceOf(BinaryDocumentSnapshot.class, binary);
        store.publishBacking(binary);
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Delete").delete(binary).build()).status());
        assertFalse(Files.exists(payload));

        WorkspaceEditResult undone = onFx(service::undo);

        assertEquals(Status.APPLIED, undone.status(), undone.problems()::toString);
        assertArrayEquals(bytes, Files.readAllBytes(payload));
        var restored = assertInstanceOf(BinaryDocumentSnapshot.class, store.current(id).orElseThrow());
        assertArrayEquals(bytes, restored.copyBytes());
        assertEquals(binary.languageId(), restored.languageId());
        assertTrue(restored.version().isAfter(binary.version()));
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertFalse(Files.exists(payload));
    }

    @Test
    public void caseOnlyRenameLeftAtItsTemporaryNameIsNotReportedAsRolledBack() throws Exception {
        TextDocumentSnapshot document = disk("Case.txt", "content");
        Assumptions.assumeTrue(Files.exists(root.resolve("CASE.TXT")), "Requires a case-insensitive filesystem");
        files.failure = (operation, path) -> operation.equals("move")
            && path.getFileName().toString().contains(".railroad-rename-");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Case")
            .rename(document, DocumentUri.fromPath(root.resolve("case.txt"))).build());

        assertEquals(Status.INCONSISTENT, result.status(), result.problems()::toString);
        assertTrue(result.problems().stream().anyMatch(problem -> problem.kind() == Kind.IO_FAILURE
            && document.uri().equals(problem.uri())), result.problems()::toString);
        assertFalse(Files.exists(root.resolve("Case.txt")));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void caseOnlyRenameRollbackRestoresTheOriginalNameAfterATransientFailure() throws Exception {
        TextDocumentSnapshot document = disk("Case.txt", "content");
        Assumptions.assumeTrue(Files.exists(root.resolve("CASE.TXT")), "Requires a case-insensitive filesystem");
        var failures = new AtomicInteger();
        files.failure = (operation, path) -> operation.equals("move")
            && path.getFileName().toString().contains(".railroad-rename-") && failures.getAndIncrement() == 0;

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Case")
            .rename(document, DocumentUri.fromPath(root.resolve("case.txt"))).build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals("Case.txt", root.resolve("Case.txt").toRealPath().getFileName().toString());
        assertEquals("content", Files.readString(root.resolve("Case.txt")));
        assertSame(document, store.current(document.id()).orElseThrow());
    }

    @Test
    public void caseOnlyRenameRollbackRecoversAFileLeftAtItsTemporaryName() throws Exception {
        TextDocumentSnapshot document = disk("Case.txt", "content");
        Assumptions.assumeTrue(Files.exists(root.resolve("CASE.TXT")), "Requires a case-insensitive filesystem");
        var failures = new AtomicInteger();
        files.failure = (operation, path) -> operation.equals("move")
            && path.getFileName().toString().contains(".railroad-rename-") && failures.getAndIncrement() < 2;

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Case")
            .rename(document, DocumentUri.fromPath(root.resolve("case.txt"))).build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals(3, failures.get());
        assertEquals("Case.txt", root.resolve("Case.txt").toRealPath().getFileName().toString());
        assertEquals("content", Files.readString(root.resolve("Case.txt")));
        try (var listing = Files.list(root)) {
            assertEquals(List.of("Case.txt"), listing.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    public void undoRestoresADeletedFileUnderItsOriginalIdentity() throws Exception {
        TextDocumentSnapshot target = disk("Target.txt", "target");
        TextDocumentSnapshot source = disk("Source.txt", "source");
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Edit")
            .replace(target, replacement(0, 6, "TARGET")).build()).status());
        DocumentSnapshot edited = store.current(target.id()).orElseThrow();
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Replace")
            .delete(edited).rename(source, target.uri()).build()).status());
        assertEquals(source.id(), identities.findIdentity(target.uri()).orElseThrow().id());

        WorkspaceEditResult replaceUndone = onFx(service::undo);

        assertEquals(Status.APPLIED, replaceUndone.status(), replaceUndone.problems()::toString);
        assertEquals(target.id(), identities.findIdentity(target.uri()).orElseThrow().id());
        assertEquals(source.id(), identities.findIdentity(source.uri()).orElseThrow().id());
        DocumentSnapshot restored = store.current(target.id()).orElseThrow();
        assertEquals(target.uri(), restored.uri());
        assertEquals("TARGET", ((TextDocumentSnapshot) restored).text());
        assertTrue(restored.version().isAfter(edited.version()));
        WorkspaceEditResult editUndone = onFx(service::undo);
        assertEquals(Status.APPLIED, editUndone.status(), editUndone.problems()::toString);
        assertEquals("target", Files.readString(root.resolve("Target.txt")));
        assertEquals("source", Files.readString(root.resolve("Source.txt")));
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertEquals(source.id(), identities.findIdentity(target.uri()).orElseThrow().id());
        assertEquals("source", Files.readString(root.resolve("Target.txt")));
        assertTrue(store.current(target.id()).isEmpty());
    }

    @Test
    public void deletingAHardLinkKeepsTheSurvivingAliasPublished() throws Exception {
        Path primary = root.resolve("Primary.txt");
        Path alias = root.resolve("Alias.txt");
        Files.writeString(primary, "shared");
        try {
            Files.createLink(alias, primary);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("Hard links are unavailable on this filesystem");
        }
        TextDocumentSnapshot document = publish(primary, "shared");
        assertEquals(document.id(), identities.getOrCreate(alias));

        WorkspaceEditResult deleted = apply(WorkspaceEdit.builder("Delete").delete(document).build());

        assertEquals(Status.APPLIED, deleted.status(), deleted.problems()::toString);
        assertFalse(Files.exists(primary));
        assertEquals("shared", Files.readString(alias));
        DocumentSnapshot surviving = store.current(document.id()).orElseThrow();
        assertEquals(DocumentUri.fromPath(alias), surviving.uri());
        assertEquals("shared", ((TextDocumentSnapshot) surviving).text());
        assertEquals(document.id(), identities.findIdentity(DocumentUri.fromPath(alias)).orElseThrow().id());
        assertTrue(identities.findIdentity(document.uri()).isEmpty());

        WorkspaceEditResult undone = onFx(service::undo);

        assertEquals(Status.APPLIED, undone.status(), undone.problems()::toString);
        assertEquals("shared", Files.readString(primary));
        DocumentId recreated = identities.findIdentity(document.uri()).orElseThrow().id();
        assertNotEquals(document.id(), recreated);
        assertEquals(document.uri(), store.current(recreated).orElseThrow().uri());
        assertEquals(DocumentUri.fromPath(alias), store.current(document.id()).orElseThrow().uri());
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertFalse(Files.exists(primary));
        assertTrue(store.current(recreated).isEmpty());
        assertEquals(DocumentUri.fromPath(alias), store.current(document.id()).orElseThrow().uri());
    }

    @Test
    public void rollbackOfAHardLinkDeletionRestoresItsLocation() throws Exception {
        Path primary = root.resolve("Primary.txt");
        Path alias = root.resolve("Alias.txt");
        Files.writeString(primary, "shared");
        try {
            Files.createLink(alias, primary);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("Hard links are unavailable on this filesystem");
        }
        TextDocumentSnapshot document = publish(primary, "shared");
        identities.getOrCreate(alias);
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        host.failRelocationAfter = 0;

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Delete")
            .delete(document).rename(moved, DocumentUri.fromPath(root.resolve("Elsewhere.txt"))).build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals("shared", Files.readString(primary));
        assertTrue(Files.isSameFile(primary, alias), "Rollback must restore the hard link, not a copy");
        assertEquals(document.uri(), store.current(document.id()).orElseThrow().uri());
        assertEquals(document.id(), identities.findIdentity(document.uri()).orElseThrow().id());
        assertEquals(document.id(), identities.findIdentity(DocumentUri.fromPath(alias)).orElseThrow().id());
    }

    private WorkspaceEditResult apply(WorkspaceEdit edit) throws Exception {
        return onFx(() -> service.apply(edit));
    }

    private static List<Kind> kinds(WorkspaceEditResult result) {
        return result.problems().stream().map(WorkspaceEditProblem::kind).toList();
    }

    private TextDocumentSnapshot disk(String name, String text) throws Exception {
        Path path = root.resolve(name);
        Files.writeString(path, text);
        return publish(path, text);
    }

    private TextDocumentSnapshot publish(Path path, String text) {
        DocumentId id = identities.getOrCreate(path);
        var snapshot = new TextDocumentSnapshot(id, DocumentUri.fromPath(path), DocumentVersion.initial(),
            "plaintext", DocumentOrigin.original(DocumentUri.fromPath(path)), text, StandardCharsets.UTF_8);
        store.publishBacking(snapshot);
        return snapshot;
    }

    private TextDocumentSnapshot draft(String name, String saved, String unsaved) throws Exception {
        TextDocumentSnapshot backing = disk(name, saved);
        var overlay = new TextDocumentSnapshot(backing.id(), backing.uri(), backing.version().next(), "plaintext",
            backing.origin(), unsaved, StandardCharsets.UTF_8);
        store.publishOverlay(overlay);
        return overlay;
    }

    private TextDocumentSnapshot republish(TextDocumentSnapshot snapshot, String text) {
        var newer = new TextDocumentSnapshot(snapshot.id(), snapshot.uri(), store.latestVersion(snapshot.id())
            .orElseThrow().next(), snapshot.languageId(), snapshot.origin(), text, snapshot.encoding());
        store.publishBacking(newer);
        return newer;
    }

    private TextEditorPane openEditor(String name, String text) throws Exception {
        Path path = root.resolve(name);
        Files.writeString(path, text);
        DocumentId id = identities.getOrCreate(path);
        return onFx(() -> {
            TextEditorPane editor = createEditor(id, path);
            store.publishBacking(editor.captureSnapshot());
            editor.setDocumentStore(store);
            host.editors.put(id, editor);
            return editor;
        });
    }

    /** Opens an editor over a published document the way the tab manager does, including line-ending reloads. */
    private TextEditorPane openDiscovered(Path path) throws Exception {
        DocumentId id = identities.getOrCreate(path);
        return onFx(() -> {
            TextEditorPane editor = createEditor(id, path);
            var backing = (TextDocumentSnapshot) store.current(id).orElseThrow();
            if (backing.text().equals(editor.getText())) {
                editor.restoreSavedVersion(backing.version());
            } else {
                editor.restoreSavedVersion(store.latestVersion(id).orElseThrow().next());
                store.publishBacking(editor.captureSnapshot());
            }
            editor.setDocumentStore(store);
            host.editors.put(id, editor);
            return editor;
        });
    }

    private static TextEditorPane createEditor(DocumentId id, Path path) throws ReflectiveOperationException {
        var editor = new TextEditorPane(id, path, "plaintext") {
            @Override
            protected void showExternalChangeDialog(String disk) {
            }
        };
        var field = TextEditorPane.class.getDeclaredField("changeSubscription");
        field.setAccessible(true);
        ((Subscription) field.get(editor)).unsubscribe();
        return editor;
    }

    private static TextReplacement replacement(int start, int end, String text) {
        return new TextReplacement(new TextRange(start, end), text);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread())
            return action.call();
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private final class TestHost implements WorkspaceEditorHost {
        private final Map<DocumentId, TextEditorPane> editors = new LinkedHashMap<>();
        private int relocations;
        private int failRelocationAfter = Integer.MAX_VALUE;

        @Override
        public Optional<TextEditorPane> openEditor(DocumentId id) {
            TextEditorPane editor = editors.get(id);
            return editor == null || editor.isEditorClosed() ? Optional.empty() : Optional.of(editor);
        }

        @Override
        public boolean isOpen(DocumentId id) {
            return openEditor(id).isPresent();
        }

        @Override
        public void relocate(DocumentIdentity identity, Path newPath) {
            if (relocations++ == failRelocationAfter)
                throw new IllegalStateException("Injected relocation failure");
            DocumentUri uri = DocumentUri.fromPath(newPath);
            identities.rebind(identity.id(), identity.uri(), uri);
            store.relocate(identity.id(), uri);
            openEditor(identity.id()).ifPresent(editor -> editor.rebind(newPath));
        }
    }

    private static final class FaultyFiles implements WorkspaceFiles {
        private BiPredicate<String, Path> failure = (_, _) -> false;

        private void check(String operation, Path path) throws IOException {
            if (failure.test(operation, path))
                throw new IOException("Injected " + operation + " failure for " + path);
        }

        @Override
        public boolean exists(Path path) {
            return NIO.exists(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return NIO.isDirectory(path);
        }

        @Override
        public boolean isWritable(Path path) {
            return NIO.isWritable(path);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            check("read", path);
            return NIO.read(path);
        }

        @Override
        public void overwrite(Path path, byte[] content) throws IOException {
            check("overwrite", path);
            NIO.overwrite(path, content);
        }

        @Override
        public void createFile(Path path, byte[] content) throws IOException {
            check("create", path);
            NIO.createFile(path, content);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            check("link", link);
            NIO.createLink(link, existing);
        }

        @Override
        public void createDirectory(Path path) throws IOException {
            check("mkdir", path);
            NIO.createDirectory(path);
        }

        @Override
        public void delete(Path path) throws IOException {
            check("delete", path);
            NIO.delete(path);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            check("move", source);
            NIO.move(source, target);
        }
    }
}
