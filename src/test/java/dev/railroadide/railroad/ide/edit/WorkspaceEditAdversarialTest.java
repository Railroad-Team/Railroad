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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceEditAdversarialTest {
    @TempDir
    public Path root;
    private Path base;
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
    public void createService() throws Exception {
        host = new TestHost();
        resetService(root);
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
    public void renameChainOntoALocationVacatedEarlierInTheEditApplies() throws Exception {
        TextDocumentSnapshot a = disk("A.txt", "a");
        TextDocumentSnapshot c = disk("C.txt", "c");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Chain")
            .rename(a, uri("B.txt"))
            .rename(c, a.uri())
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals("a", Files.readString(root.resolve("B.txt")));
        assertEquals("c", Files.readString(root.resolve("A.txt")));
        assertFalse(Files.exists(root.resolve("C.txt")));
        assertEquals(c.id(), identities.findIdentity(a.uri()).orElseThrow().id());
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("a", Files.readString(root.resolve("A.txt")));
        assertEquals("c", Files.readString(root.resolve("C.txt")));
        assertFalse(Files.exists(root.resolve("B.txt")));
    }

    @Test
    public void moveOntoALocationVacatedByADeleteInTheSameEditApplies() throws Exception {
        TextDocumentSnapshot moved = disk("A.txt", "a");
        TextDocumentSnapshot deleted = disk("B.txt", "b");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Replace")
            .delete(deleted)
            .rename(moved, deleted.uri())
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals("a", Files.readString(root.resolve("B.txt")));
        assertFalse(Files.exists(root.resolve("A.txt")));
        assertEquals(deleted.uri(), store.current(moved.id()).orElseThrow().uri());
    }

    @Test
    public void undoOfARenameFollowedByCreationAtTheVacatedPathSucceeds() throws Exception {
        TextDocumentSnapshot original = disk("A.txt", "a");
        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Extract")
            .rename(original, uri("B.txt"))
            .createText(original.uri(), "replacement")
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        assertEquals("replacement", Files.readString(root.resolve("A.txt")));

        WorkspaceEditResult undone = onFx(service::undo);

        assertEquals(Status.APPLIED, undone.status(), undone.problems()::toString);
        assertEquals("a", Files.readString(root.resolve("A.txt")));
        assertFalse(Files.exists(root.resolve("B.txt")));
        assertEquals(original.uri(), store.current(original.id()).orElseThrow().uri());
    }

    @Test
    public void rolledBackEditorChangeCannotBeResurrectedByLocalRedo() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        host.relocationFailure = new IllegalStateException("injected relocation failure");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Fails")
            .replace(editorSnapshot, replacement(0, 6, "CHANGED"))
            .rename(moved, uri("Destination.txt"))
            .build());
        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals("editor", onFx(editor::getText));
        host.relocationFailure = null;

        onFx(() -> {
            editor.redo();
            return null;
        });

        assertEquals("editor", onFx(editor::getText), "A rolled-back workspace edit must not be redoable locally");
    }

    @Test
    public void postApplySelectionOnAnUnreconciledEditorDoesNotEscapeAfterApplying() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot editorSnapshot = onFx(() -> editor.textEditorAccess().snapshot());
        store.publishBacking(new TextDocumentSnapshot(editorSnapshot.id(), editorSnapshot.uri(),
            editorSnapshot.version().next(), editorSnapshot.languageId(), editorSnapshot.origin(), "external",
            StandardCharsets.UTF_8));
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");

        WorkspaceEditResult result;
        try {
            result = apply(WorkspaceEdit.builder("Edit")
                .replace(closed, replacement(0, 6, "CLOSED"))
                .select(editorSnapshot.id(), new TextSelection(0, 0))
                .build());
        } catch (ExecutionException exception) {
            fail("apply threw after writing Closed.txt=" + Files.readString(root.resolve("Closed.txt"))
                + ", canUndo=" + onFx(service::canUndo), exception.getCause());
            return;
        }

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertTrue(onFx(service::canUndo));
    }

    @Test
    public void editorUndoOfANonNativeWorkspaceUndoReappliesTheWholeEdit() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Edit")
            .replace(onFx(() -> editor.textEditorAccess().snapshot()), replacement(0, 6, "EDITOR"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        onFx(() -> {
            editor.appendText("x");
            editor.getUndoManager().preventMerge();
            editor.deleteText(6, 7);
            editor.getUndoManager().preventMerge();
            return null;
        });
        assertEquals("EDITOR", onFx(editor::getText));
        WorkspaceEditResult undone = onFx(service::undo);
        assertEquals(Status.APPLIED, undone.status(), undone.problems()::toString);
        assertEquals("editor", onFx(editor::getText));
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));

        onFx(() -> {
            editor.undo();
            return null;
        });

        assertEquals("EDITOR", onFx(editor::getText));
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));
        assertTrue(onFx(service::canUndo));
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("editor", onFx(editor::getText));
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));
    }

    @Test
    public void transientReadFailureDuringUndoDoesNotDiscardTheEdit() throws Exception {
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Edit")
            .replace(closed, replacement(0, 6, "CLOSED")).build()).status());
        AtomicBoolean fired = arm("read", 1);

        WorkspaceEditResult failed = onFx(service::undo);
        files.failure = (_, _) -> false;

        assertTrue(fired.get());
        assertNotEquals(Status.APPLIED, failed.status());
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));
        WorkspaceEditResult retried = onFx(service::undo);
        assertEquals(Status.APPLIED, retried.status(),
            "first attempt " + failed.status() + " " + failed.problems() + " discarded the unchanged edit");
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));
    }

    @Test
    public void unencodableResultIsRejectedBeforeAnyFileIsWritten() throws Exception {
        TextDocumentSnapshot first = disk("First.txt", "first");
        TextDocumentSnapshot second = disk("Second.txt", "a😀b");
        var overwrites = new AtomicInteger();
        files.failure = (operation, _) -> {
            if (operation.equals("overwrite")) {
                overwrites.incrementAndGet();
            }
            return false;
        };
        WorkspaceEdit edit = WorkspaceEdit.builder("Split pair")
            .replace(first, replacement(0, 5, "FIRST"))
            .replace(second, replacement(1, 2, "x"))
            .build();

        WorkspaceEditPreview preview = onFx(() -> service.preview(edit));
        WorkspaceEditResult result = apply(edit);

        assertEquals("first", Files.readString(root.resolve("First.txt")));
        assertEquals(Status.REJECTED, result.status(), result.problems()::toString);
        assertEquals(0, overwrites.get(), "Files were written before the edit was found to be unencodable");
        assertFalse(preview.applicable());
    }

    @Test
    public void undoOfCreatedFileWithUnencodableDraftIsRejectedRatherThanThrown() throws Exception {
        DocumentUri created = uri("Created.txt");
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Create").createText(created, "created").build())
            .status());
        DocumentId id = identities.findIdentity(created).orElseThrow().id();
        var current = (TextDocumentSnapshot) store.current(id).orElseThrow();
        store.publishOverlay(new TextDocumentSnapshot(id, current.uri(), current.version().next(),
            current.languageId(), current.origin(), "bad\uD800", StandardCharsets.UTF_8));

        WorkspaceEditResult result;
        try {
            result = onFx(service::undo);
        } catch (ExecutionException exception) {
            fail("undo threw instead of rejecting", exception.getCause());
            return;
        }

        assertEquals(Status.REJECTED, result.status());
        assertTrue(Files.exists(root.resolve("Created.txt")));
    }

    @Test
    public void outOfBoundsSelectionForADocumentWithoutAnEditorIsIgnored() throws Exception {
        TextDocumentSnapshot closed = disk("Closed.txt", "abc");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Shrink")
            .replace(closed, replacement(0, 3, "x"))
            .select(closed.id(), new TextSelection(0, 2))
            .build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertEquals("x", Files.readString(root.resolve("Closed.txt")));
    }

    @Test
    public void publicationRollbackRestoresStoreFilesAndIdentities() throws Exception {
        TextDocumentSnapshot written = disk("Written.txt", "original");
        TextDocumentSnapshot draft = draft("Draft.txt", "saved", "unsaved");
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        DocumentUri created = uri("made/Created.txt");
        Map<String, String> treeBefore = tree(root);
        Map<DocumentId, DocumentSnapshot> documentsBefore = store.capture().documents();
        host.relocationFailure = new IllegalStateException("injected relocation failure");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Fails late")
            .replace(written, replacement(0, 8, "CHANGED"))
            .replace(draft, replacement(0, 7, "UNSAVED"))
            .createText(created, "created")
            .rename(moved, uri("Destination.txt"))
            .build());

        assertEquals(Status.ROLLED_BACK, result.status(), result.problems()::toString);
        assertEquals(treeBefore, tree(root));
        assertEquivalent(documentsBefore, store.capture().documents(), "after publication rollback");
        assertNotNull(store.state(draft.id()).orElseThrow().overlay());
        assertEquals(moved.id(), identities.findIdentity(moved.uri()).orElseThrow().id());
        assertTrue(identities.findIdentity(created).isEmpty(),
            "Rollback left an identity registered for the never-created file");
    }

    @Test
    public void failureAtAnyFilesystemStepOfApplyOrUndoLeavesNoPartialState() throws Exception {
        int run = 0;
        List<String> applyOutcomes = new ArrayList<>();
        List<String> undoOutcomes = new ArrayList<>();
        for (String operation : List.of("read", "overwrite", "create", "mkdir", "delete", "move")) {
            for (int occurrence = 1; occurrence < 40; occurrence++) {
                resetService(Files.createDirectories(root.resolve("apply" + run++)));
                WorkspaceEdit edit = buildFaultScenario();
                String label = "apply, failing " + operation + " #" + occurrence;
                Map<String, String> treeBefore = tree(base);
                Map<DocumentId, DocumentSnapshot> documentsBefore = store.capture().documents();
                AtomicBoolean fired = arm(operation, occurrence);

                WorkspaceEditResult result = apply(edit);
                files.failure = (_, _) -> false;

                if (!fired.get()) {
                    assertEquals(Status.APPLIED, result.status(), label + ": " + result.problems());
                    break;
                }
                applyOutcomes.add(label + " -> " + result.status());
                assertTrue(result.status() == Status.REJECTED || result.status() == Status.ROLLED_BACK,
                    label + ": " + result.status() + " " + result.problems());
                assertEquals(treeBefore, tree(base), label);
                assertEquivalent(documentsBefore, store.capture().documents(), label);
                assertFalse(onFx(service::canUndo), label);
            }
        }
        for (String operation : List.of("read", "overwrite", "create", "mkdir", "delete", "move")) {
            for (int occurrence = 1; occurrence < 40; occurrence++) {
                resetService(Files.createDirectories(root.resolve("undo" + run++)));
                WorkspaceEdit edit = buildFaultScenario();
                String label = "undo, failing " + operation + " #" + occurrence;
                Map<String, String> treeBefore = tree(base);
                Map<DocumentId, DocumentSnapshot> documentsBefore = store.capture().documents();
                WorkspaceEditResult applied = apply(edit);
                assertEquals(Status.APPLIED, applied.status(), label + ": " + applied.problems());
                Map<String, String> treeApplied = tree(base);
                Map<DocumentId, DocumentSnapshot> documentsApplied = store.capture().documents();
                AtomicBoolean fired = arm(operation, occurrence);

                WorkspaceEditResult result = onFx(service::undo);
                files.failure = (_, _) -> false;

                if (!fired.get()) {
                    assertEquals(Status.APPLIED, result.status(), label + ": " + result.problems());
                    assertEquals(treeBefore, tree(base), label);
                    assertEquivalent(documentsBefore, store.capture().documents(), label);
                    break;
                }
                undoOutcomes.add(label + " -> " + result.status());
                if (result.status() == Status.APPLIED) {
                    assertEquals(withoutDirectories(treeBefore), withoutDirectories(tree(base)), label);
                    assertEquivalent(documentsBefore, store.capture().documents(), label);
                    continue;
                }
                assertTrue(result.status() == Status.REJECTED || result.status() == Status.ROLLED_BACK,
                    label + ": " + result.status() + " " + result.problems());
                assertEquals(treeApplied, tree(base), label);
                assertEquivalent(documentsApplied, store.capture().documents(), label);
                if (result.status() == Status.REJECTED)
                    continue;
                assertTrue(onFx(service::canUndo), label);
                WorkspaceEditResult retried = onFx(service::undo);
                assertEquals(Status.APPLIED, retried.status(), label + " retry: " + retried.problems());
                assertEquals(treeBefore, tree(base), label + " retry");
                assertEquivalent(documentsBefore, store.capture().documents(), label + " retry");
            }
        }
        assertTrue(applyOutcomes.size() > 10, applyOutcomes::toString);
        assertTrue(undoOutcomes.size() > 10, undoOutcomes::toString);
    }

    @Test
    public void draftRenamedWithATextEditUndoesAndRedoes() throws Exception {
        TextDocumentSnapshot draft = draft("Draft.txt", "saved", "unsaved");
        DocumentUri destination = uri("dir/Renamed.txt");

        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Rename draft")
            .replace(draft, replacement(0, 7, "UNSAVED"))
            .rename(draft, destination)
            .build());

        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        assertEquals("saved", Files.readString(root.resolve("dir/Renamed.txt")));
        assertFalse(Files.exists(root.resolve("Draft.txt")));
        DocumentState state = store.state(draft.id()).orElseThrow();
        assertEquals("UNSAVED", ((TextDocumentSnapshot) state.overlay()).text());
        assertEquals(destination, state.overlay().uri());
        assertEquals(destination, state.backing().uri());

        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("saved", Files.readString(root.resolve("Draft.txt")));
        assertFalse(Files.exists(root.resolve("dir")));
        state = store.state(draft.id()).orElseThrow();
        assertEquals("unsaved", ((TextDocumentSnapshot) state.overlay()).text());
        assertEquals(draft.uri(), state.overlay().uri());

        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertEquals("UNSAVED", ((TextDocumentSnapshot) store.current(draft.id()).orElseThrow()).text());
        assertEquals(destination, store.current(draft.id()).orElseThrow().uri());
    }

    @Test
    public void editorRenamedWithATextEditUndoesAndRedoesThroughEditorCommands() throws Exception {
        TextEditorPane editor = openEditor("Old.txt", "class Old {}");
        TextDocumentSnapshot snapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot closed = disk("Closed.txt", "Old");
        Path destination = root.resolve("pkg/New.txt");

        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Rename class")
            .replace(snapshot, replacement(6, 9, "New"))
            .replace(closed, replacement(0, 3, "New"))
            .rename(snapshot, DocumentUri.fromPath(destination))
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);

        onFx(() -> {
            editor.undo();
            return null;
        });
        assertEquals("class Old {}", onFx(editor::getText));
        assertEquals(root.resolve("Old.txt").toAbsolutePath().normalize(), editor.getFilePath());
        assertTrue(Files.exists(root.resolve("Old.txt")));
        assertFalse(Files.exists(root.resolve("pkg")));
        assertEquals("Old", Files.readString(root.resolve("Closed.txt")));
        assertEquals(snapshot.uri(), store.current(snapshot.id()).orElseThrow().uri());

        onFx(() -> {
            editor.redo();
            return null;
        });
        assertEquals("class New {}", onFx(editor::getText));
        assertEquals(destination.toAbsolutePath().normalize(), editor.getFilePath());
        assertEquals("New", Files.readString(root.resolve("Closed.txt")));

        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("class Old {}", onFx(editor::getText));
        assertFalse(Files.exists(root.resolve("pkg")));
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertEquals("class New {}", onFx(editor::getText));
    }

    @Test
    public void nestedDirectoriesAreRemovedAndRecreatedAcrossUndoRedoCycles() throws Exception {
        DocumentUri created = uri("a/b/c/X.txt");
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Create").createText(created, "x").build())
            .status());

        for (int cycle = 0; cycle < 3; cycle++) {
            assertEquals(Status.APPLIED, onFx(service::undo).status(), "undo " + cycle);
            assertFalse(Files.exists(root.resolve("a")), "undo " + cycle);
            assertEquals(Status.APPLIED, onFx(service::redo).status(), "redo " + cycle);
            assertEquals("x", Files.readString(root.resolve("a/b/c/X.txt")), "redo " + cycle);
        }

        Files.writeString(root.resolve("a/b/Other.txt"), "other");
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertFalse(Files.exists(root.resolve("a/b/c")));
        assertEquals("other", Files.readString(root.resolve("a/b/Other.txt")));
    }

    @Test
    public void severalHistoryEntriesOnOneDocumentUndoAndRedoInOrder() throws Exception {
        TextDocumentSnapshot document = disk("Doc.txt", "a");
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("To b")
            .replace(document, replacement(0, 1, "b")).build()).status());
        var second = (TextDocumentSnapshot) store.current(document.id()).orElseThrow();
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("To c")
            .replace(second, replacement(0, 1, "c")).build()).status());

        List<String> observed = new ArrayList<>();
        DocumentVersion previous = store.current(document.id()).orElseThrow().version();
        for (Callable<WorkspaceEditResult> step : List.<Callable<WorkspaceEditResult>>of(service::undo,
            service::undo, service::redo, service::undo, service::redo, service::redo)) {
            WorkspaceEditResult result = onFx(step);
            assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
            DocumentSnapshot current = store.current(document.id()).orElseThrow();
            assertTrue(current.version().isAfter(previous));
            previous = current.version();
            observed.add(Files.readString(root.resolve("Doc.txt")) + "/" + ((TextDocumentSnapshot) current).text());
        }

        assertEquals(List.of("b/b", "a/a", "b/b", "a/a", "b/b", "c/c"), observed);
        assertFalse(onFx(service::canRedo));
        assertEquals(Status.REJECTED, onFx(service::redo).status());
    }

    @Test
    public void staleVersionLocationContentAndDiskChangesAreRejected() throws Exception {
        TextDocumentSnapshot versioned = disk("Versioned.txt", "same");
        republish(versioned, "same");
        TextDocumentSnapshot relocated = disk("Relocated.txt", "text");
        Files.move(root.resolve("Relocated.txt"), root.resolve("Elsewhere.txt"));
        store.relocate(relocated.id(), uri("Elsewhere.txt"));
        TextDocumentSnapshot forged = disk("Forged.txt", "real");
        var forgery = new TextDocumentSnapshot(forged.id(), forged.uri(), forged.version(), forged.languageId(),
            forged.origin(), "fake", StandardCharsets.UTF_8);
        TextDocumentSnapshot unobserved = disk("Unobserved.txt", "one");
        Files.writeString(root.resolve("Unobserved.txt"), "two");

        for (WorkspaceChange change : List.<WorkspaceChange>of(
            new WorkspaceChange.TextEdit(versioned, List.of(replacement(0, 1, "x"))),
            new WorkspaceChange.TextEdit(relocated, List.of(replacement(0, 1, "x"))),
            new WorkspaceChange.TextEdit(forgery, List.of(replacement(0, 1, "x"))),
            new WorkspaceChange.TextEdit(unobserved, List.of(replacement(0, 1, "x"))),
            new WorkspaceChange.DeleteFile(versioned, null),
            new WorkspaceChange.RenameFile(relocated, uri("Moved.txt"), null),
            new WorkspaceChange.DeleteFile(forgery, null),
            new WorkspaceChange.RenameFile(unobserved, uri("Moved.txt"), null))) {
            WorkspaceEditResult result = apply(WorkspaceEdit.builder("Stale").change(change).build());
            assertEquals(List.of(Kind.STALE), kinds(result), change::toString);
        }
        assertEquals("same", Files.readString(root.resolve("Versioned.txt")));
        assertEquals("real", Files.readString(root.resolve("Forged.txt")));
        assertEquals("two", Files.readString(root.resolve("Unobserved.txt")));
    }

    @Test
    public void crlfReplacementInAnEditorRoundTripsThroughUndoAndRedo() throws Exception {
        TextEditorPane editor = openEditor("Lines.txt", "line");
        TextDocumentSnapshot snapshot = onFx(() -> editor.textEditorAccess().snapshot());
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");

        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Lines")
            .replace(snapshot, replacement(4, 4, "\r\nnext"))
            .replace(closed, replacement(0, 6, "a\r\nb"))
            .build());
        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        String editorAfter = onFx(editor::getText);
        assertTrue(editorAfter.endsWith("next"), editorAfter);
        assertEquals("a\r\nb", Files.readString(root.resolve("Closed.txt")));

        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("line", onFx(editor::getText));
        assertEquals("closed", Files.readString(root.resolve("Closed.txt")));
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertEquals(editorAfter, onFx(editor::getText));
        assertEquals("a\r\nb", Files.readString(root.resolve("Closed.txt")));
    }

    @Test
    public void caseOnlyRenameChangesTheNameOnDisk() throws Exception {
        TextDocumentSnapshot document = disk("case.txt", "text");

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Case")
            .rename(document, uri("CASE.txt")).build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        try (Stream<Path> listing = Files.list(root)) {
            assertEquals(List.of("CASE.txt"), listing.map(path -> path.getFileName().toString()).toList());
        }
        assertEquals(uri("CASE.txt"), store.current(document.id()).orElseThrow().uri());
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        try (Stream<Path> listing = Files.list(root)) {
            assertEquals(List.of("case.txt"), listing.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    public void closedServiceRejectsHistoryAndEditorsFallBackToLocalUndo() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        apply(WorkspaceEdit.builder("Edit")
            .replace(onFx(() -> editor.textEditorAccess().snapshot()), replacement(0, 6, "EDITOR"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());

        onFx(() -> {
            service.close();
            editor.undo();
            return null;
        });

        assertEquals("editor", onFx(editor::getText));
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));
        assertFalse(onFx(service::canUndo));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> onFx(service::undo));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    public void reentrantApplyFromAStoreObserverIsRejectedWithoutDisturbingTheOuterEdit() throws Exception {
        TextDocumentSnapshot outer = disk("Outer.txt", "outer");
        TextDocumentSnapshot inner = disk("Inner.txt", "inner");
        var nested = new AtomicReference<Throwable>();
        var calls = new AtomicInteger();
        store.onChange(() -> {
            if (calls.getAndIncrement() > 0)
                return;
            try {
                service.apply(WorkspaceEdit.builder("Nested").replace(inner, replacement(0, 5, "INNER")).build());
            } catch (Throwable throwable) {
                nested.set(throwable);
            }
            try {
                service.undo();
            } catch (Throwable throwable) {
                nested.get().addSuppressed(throwable);
            }
        });

        WorkspaceEditResult result = apply(WorkspaceEdit.builder("Outer")
            .replace(outer, replacement(0, 5, "OUTER")).build());

        assertEquals(Status.APPLIED, result.status(), result.problems()::toString);
        assertInstanceOf(IllegalStateException.class, nested.get());
        assertInstanceOf(IllegalStateException.class, nested.get().getSuppressed()[0]);
        assertEquals("OUTER", Files.readString(root.resolve("Outer.txt")));
        assertEquals("inner", Files.readString(root.resolve("Inner.txt")));
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("outer", Files.readString(root.resolve("Outer.txt")));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void undoIsRejectedWhileACreatedFileIsOpenInAnEditor() throws Exception {
        DocumentUri created = uri("Created.txt");
        assertEquals(Status.APPLIED, apply(WorkspaceEdit.builder("Create").createText(created, "created").build())
            .status());
        DocumentId id = identities.findIdentity(created).orElseThrow().id();
        TextEditorPane editor = openExistingEditor(root.resolve("Created.txt"), id);

        WorkspaceEditResult result = onFx(service::undo);

        assertEquals(Status.REJECTED, result.status());
        assertEquals(List.of(Kind.IN_USE), kinds(result));
        assertTrue(Files.exists(root.resolve("Created.txt")));
        assertEquals("created", onFx(editor::getText));
        assertFalse(onFx(service::canUndo));
    }

    @Test
    public void binaryDeleteAndCreateUndoAndRedoRestoreExactBytes() throws Exception {
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xff, (byte) 0xfe};
        Path image = root.resolve("image.png");
        Files.write(image, bytes);
        DocumentId id = identities.getOrCreate(image);
        DocumentSnapshot binary = ProjectDocumentSynchronizer.backingSnapshot(id, DocumentUri.fromPath(image),
            DocumentVersion.initial(), image, bytes);
        assertInstanceOf(BinaryDocumentSnapshot.class, binary);
        store.publishBacking(binary);
        DocumentUri copy = uri("assets/copy.png");

        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Binary")
            .delete(binary)
            .change(new WorkspaceChange.CreateFile(copy, bytes, null))
            .build());

        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        assertFalse(Files.exists(image));
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertArrayEquals(bytes, Files.readAllBytes(image));
        assertFalse(Files.exists(root.resolve("assets")));
        assertInstanceOf(BinaryDocumentSnapshot.class, store.current(id).orElseThrow());
        assertEquals(Status.APPLIED, onFx(service::redo).status());
        assertFalse(Files.exists(image));
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("assets/copy.png")));
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertArrayEquals(bytes, Files.readAllBytes(image));
    }

    @Test
    public void emptyDocumentsEndInsertionsAndSurrogatePairsRoundTrip() throws Exception {
        TextDocumentSnapshot empty = disk("Empty.txt", "");
        TextDocumentSnapshot end = disk("End.txt", "end");
        TextDocumentSnapshot pair = disk("Pair.txt", "a😀b");
        TextDocumentSnapshot noop = disk("Noop.txt", "same");

        WorkspaceEditResult applied = apply(WorkspaceEdit.builder("Edges")
            .replace(empty, replacement(0, 0, "filled"))
            .replace(end, replacement(3, 3, "!"))
            .replace(pair, replacement(1, 3, "😁😂"))
            .replace(noop, replacement(0, 4, "same"), replacement(4, 4, ""))
            .build());

        assertEquals(Status.APPLIED, applied.status(), applied.problems()::toString);
        assertEquals("filled", Files.readString(root.resolve("Empty.txt")));
        assertEquals("end!", Files.readString(root.resolve("End.txt")));
        assertEquals("a😁😂b", Files.readString(root.resolve("Pair.txt")));
        assertEquals(Status.APPLIED, onFx(service::undo).status());
        assertEquals("", Files.readString(root.resolve("Empty.txt")));
        assertEquals("end", Files.readString(root.resolve("End.txt")));
        assertEquals("a😀b", Files.readString(root.resolve("Pair.txt")));
        assertEquals("same", Files.readString(root.resolve("Noop.txt")));
    }

    @Test
    public void failedRoutedUndoChangesNothingThenRepeatUndoesOnlyTheEditor() throws Exception {
        TextEditorPane editor = openEditor("Editor.txt", "editor");
        TextDocumentSnapshot closed = disk("Closed.txt", "closed");
        apply(WorkspaceEdit.builder("Edit")
            .replace(onFx(() -> editor.textEditorAccess().snapshot()), replacement(0, 6, "EDITOR"))
            .replace(closed, replacement(0, 6, "CLOSED"))
            .build());
        files.failure = (operation, _) -> operation.equals("overwrite");

        onFx(() -> {
            editor.undo();
            return null;
        });
        files.failure = (_, _) -> false;

        assertEquals("EDITOR", onFx(editor::getText));
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));
        assertEquals("CLOSED", ((TextDocumentSnapshot) store.current(closed.id()).orElseThrow()).text());
        assertFalse(onFx(service::canUndo));

        onFx(() -> {
            editor.undo();
            return null;
        });
        assertEquals("editor", onFx(editor::getText));
        assertEquals("CLOSED", Files.readString(root.resolve("Closed.txt")));
    }

    private WorkspaceEdit buildFaultScenario() throws Exception {
        TextDocumentSnapshot edited = disk("Edit.txt", "edit me");
        TextDocumentSnapshot draft = draft("Draft.txt", "saved", "unsaved");
        TextDocumentSnapshot moved = disk("Moved.txt", "moved");
        TextDocumentSnapshot gone = disk("Gone.txt", "gone");
        return WorkspaceEdit.builder("Scenario")
            .replace(edited, replacement(0, 4, "EDIT"))
            .replace(draft, replacement(0, 7, "UNSAVED"))
            .replace(moved, replacement(0, 5, "MOVED"))
            .createText(DocumentUri.fromPath(base.resolve("made/deep/New.txt")), "new")
            .delete(gone)
            .rename(moved, DocumentUri.fromPath(base.resolve("dest/sub/Moved.txt")))
            .build();
    }

    private AtomicBoolean arm(String operation, int occurrence) {
        var count = new AtomicInteger();
        var fired = new AtomicBoolean();
        files.failure = (candidate, _) -> {
            if (!candidate.equals(operation) || count.incrementAndGet() != occurrence)
                return false;
            fired.set(true);
            return true;
        };
        return fired;
    }

    private void resetService(Path projectRoot) throws Exception {
        if (service != null) {
            onFx(() -> {
                service.close();
                return null;
            });
        }
        base = projectRoot;
        store = new DefaultDocumentStore("workspace-edits", new WorkspaceConfigurationSnapshot(0, Set.of()));
        identities = new DocumentIdentityRegistry();
        files = new FaultyFiles();
        service = new WorkspaceEditService(projectRoot, store, identities, host, files);
    }

    private static Map<String, String> tree(Path directory) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.toList()) {
                if (path.equals(directory))
                    continue;
                String name = directory.relativize(path).toString().replace('\\', '/');
                entries.put(name, Files.isDirectory(path) ? "<dir>" : Files.readString(path));
            }
        }
        return entries;
    }

    private static Map<String, String> withoutDirectories(Map<String, String> tree) {
        Map<String, String> filesOnly = new TreeMap<>(tree);
        filesOnly.values().removeIf("<dir>"::equals);
        return filesOnly;
    }

    private static void assertEquivalent(
        Map<DocumentId, DocumentSnapshot> expected,
        Map<DocumentId, DocumentSnapshot> actual,
        String label
    ) {
        assertEquals(expected.keySet(), actual.keySet(), label + ": published documents");
        for (Map.Entry<DocumentId, DocumentSnapshot> entry : expected.entrySet()) {
            DocumentSnapshot before = entry.getValue();
            DocumentSnapshot after = actual.get(entry.getKey());
            assertEquals(before.uri(), after.uri(), label + ": location");
            assertFalse(after.version().compareTo(before.version()) < 0, label + ": version regressed");
            if (before instanceof TextDocumentSnapshot text) {
                assertEquals(text.text(), ((TextDocumentSnapshot) after).text(), label + ": text of " + before.uri());
            } else {
                assertEquals(((BinaryDocumentSnapshot) before).bytes(), ((BinaryDocumentSnapshot) after).bytes(),
                    label + ": bytes of " + before.uri());
            }
        }
    }

    private WorkspaceEditResult apply(WorkspaceEdit edit) throws Exception {
        return onFx(() -> service.apply(edit));
    }

    private static List<Kind> kinds(WorkspaceEditResult result) {
        return result.problems().stream().map(WorkspaceEditProblem::kind).toList();
    }

    private DocumentUri uri(String name) {
        return DocumentUri.fromPath(base.resolve(name));
    }

    private TextDocumentSnapshot disk(String name, String text) throws Exception {
        Path path = base.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
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
        Path path = base.resolve(name);
        Files.writeString(path, text);
        DocumentId id = identities.getOrCreate(path);
        return onFx(() -> {
            TextEditorPane editor = newEditor(id, path);
            store.publishBacking(editor.captureSnapshot());
            editor.setDocumentStore(store);
            host.editors.put(id, editor);
            return editor;
        });
    }

    private TextEditorPane openExistingEditor(Path path, DocumentId id) throws Exception {
        return onFx(() -> {
            TextEditorPane editor = newEditor(id, path);
            editor.restoreSavedVersion(store.state(id).orElseThrow().backing().version());
            editor.setDocumentStore(store);
            host.editors.put(id, editor);
            return editor;
        });
    }

    private static TextEditorPane newEditor(DocumentId id, Path path) throws Exception {
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
        private RuntimeException relocationFailure;

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
            if (relocationFailure != null)
                throw relocationFailure;
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
