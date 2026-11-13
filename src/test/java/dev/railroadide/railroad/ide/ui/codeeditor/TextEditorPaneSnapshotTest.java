package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.plugin.spi.editor.*;
import dev.railroadide.railroad.plugin.spi.events.DocumentModifiedEvent;
import org.fxmisc.richtext.model.PlainTextChange;
import dev.railroadide.railroad.ide.ui.editor.EditorSaveState;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import dev.railroadide.railroad.debug.breakpoint.BreakpointService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static dev.railroadide.railroad.ide.ui.editor.EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID;

public class TextEditorPaneSnapshotTest {
    @TempDir
    public Path tempDirectory;

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
        LanguageSupports.initialize();
    }

    @Test
    public void capturesStableIdentityAndVersionUntilTextChanges() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String editedText = "class Example { int value; }";
        Files.writeString(file, originalText);
        var documentId = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java")).id();
        var openEditor = new FutureTask<>(() -> new TextEditorPane(documentId, file, "java"));
        Platform.runLater(openEditor);
        TextEditorPane editor = openEditor.get(10, TimeUnit.SECONDS);

        try {
            onFxThread(() -> {
                var original = editor.captureSnapshot();
                var unchanged = editor.captureSnapshot();

                assertEquals(originalText, original.text());
                assertSame(documentId, original.id());
                assertEquals(original.id(), unchanged.id());
                assertEquals(original.version(), unchanged.version());

                editor.replaceText(editedText);
                var edited = editor.captureSnapshot();

                assertEquals(original.id(), edited.id());
                assertTrue(edited.version().compareTo(original.version()) > 0);
                assertEquals(editedText, edited.text());
                assertEquals(originalText, original.text());
                assertEquals(originalText, unchanged.text());

                var editedUnchanged = editor.captureSnapshot();
                assertEquals(edited.id(), editedUnchanged.id());
                assertEquals(edited.version(), editedUnchanged.version());
            });
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            });
        }
    }

    @Test
    public void savingEditedTextPreservesDocumentIdentityAndContentVersion() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String editedText = "class Example { int value; }";
        Files.writeString(file, originalText);
        var documentId = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java")).id();
        var openEditor = new FutureTask<>(() -> new TextEditorPane(documentId, file, "java"));
        Platform.runLater(openEditor);
        TextEditorPane editor = openEditor.get(10, TimeUnit.SECONDS);

        try {
            onFxThread(() -> {
                var original = editor.captureSnapshot();
                assertEquals(originalText, original.text());

                editor.replaceText(editedText);
                var edited = editor.captureSnapshot();
                assertEquals(original.id(), edited.id());
                assertTrue(edited.version().compareTo(original.version()) > 0);

                assertTrue(editor.saveNow());
                var saved = editor.captureSnapshot();

                assertEquals(edited.id(), saved.id());
                assertEquals(edited.version(), saved.version());
                assertEquals(editedText, saved.text());
            });

            assertEquals(editedText, Files.readString(file));
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            });
        }
    }

    @Test
    public void externalReloadAdvancesDocumentVersion() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String updatedText = "class Example { int externalValue; }";
        Files.writeString(file, originalText);
        var documentId = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java")).id();
        var openEditor = new FutureTask<>(() -> new TextEditorPane(documentId, file, "java"));
        Platform.runLater(openEditor);
        TextEditorPane editor = openEditor.get(10, TimeUnit.SECONDS);
        var reloaded = new CompletableFuture<Void>();
        ChangeListener<String> reloadListener = (observable, oldText, newText) -> {
            if (updatedText.equals(newText)) {
                reloaded.complete(null);
            }
        };

        try {
            var captureOriginal = new FutureTask<>(() -> {
                editor.textProperty().addListener(reloadListener);
                return editor.captureSnapshot();
            });
            Platform.runLater(captureOriginal);
            var original = captureOriginal.get(10, TimeUnit.SECONDS);
            assertEquals(originalText, original.text());

            // A clean editor accepts watcher-detected disk changes without a conflict dialog.
            Files.writeString(file, updatedText);
            reloaded.get(10, TimeUnit.SECONDS);

            onFxThread(() -> {
                var updated = editor.captureSnapshot();

                assertEquals(original.id(), updated.id());
                assertTrue(updated.version().compareTo(original.version()) > 0);
                assertEquals(updatedText, updated.text());
                assertEquals(originalText, original.text());
                assertEquals(EditorSaveState.CLEAN, editor.saveState());
                assertTrue(editor.saved());
                assertFalse(editor.dirty());
                assertFalse(editor.hasPendingExternalChange());
            });
        } finally {
            onFxThread(() -> {
                editor.textProperty().removeListener(reloadListener);
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            });
        }
    }

    @Test
    public void javaEditorSnapshotsUseEditorTabIdentityBeforeAndAfterEditing() throws Exception {
        Path file = tempDirectory.resolve("Identity.java");
        Files.writeString(file, "class Identity {}");
        var document = new FileSystemDocument(file, "java");
        var identity = Services.IDE_STATE.identifyDocument(document);
        var openEditor = new FutureTask<>(() -> {
            var project = new RailroadProject(tempDirectory, "Identity test", new WritableImage(1, 1));
            return new JavaLanguageSupport().open(project, identity.id(), file);
        });
        Platform.runLater(openEditor);
        var view = openEditor.get(10, TimeUnit.SECONDS);
        TextEditorPane editor = view.activeEditor();

        try {
            onFxThread(() -> {
                var tab = new EditorTab(identity, document, view, DEFAULT_EDITOR_GROUP_ID, false, false);
                var original = editor.captureSnapshot();
                assertEquals(tab.documentId(), original.id());
                assertSame(identity.id(), original.id());
                assertEquals(Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java")).id(),
                    original.id());

                editor.replaceText("class Identity { int value; }");
                var edited = editor.captureSnapshot();

                assertEquals(tab.documentId(), edited.id());
                assertSame(original.id(), edited.id());
                assertTrue(edited.version().isAfter(original.version()));
                assertEquals("class Identity { int value; }", edited.text());
            });
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            });
        }
    }

    private static void onFxThread(Runnable action) throws Exception {
        var task = new FutureTask<Void>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void legacyChangeRangeKeepsLinesAndColumnsInTheirDeclaredOrder() throws Exception {
        var buildChange = TextEditorPane.class.getDeclaredMethod("buildChange", String.class, PlainTextChange.class);
        buildChange.setAccessible(true);
        var change = (DocumentModifiedEvent.Change) buildChange.invoke(null, "abc\nxy",
            new PlainTextChange(4, "", "xy"));
        assertEquals(new DocumentModifiedEvent.Range(1, 1, 0, 2), change.range());
    }

    @Test
    public void javaCloseVetoKeepsBreakpointTrackingUntilAcceptedClose() throws Exception {
        Path file = tempDirectory.resolve("Veto.java");
        Files.writeString(file, "class Veto {\n int field;\n}\n");
        var open = new FutureTask<>(() -> {
            var project = new RailroadProject(tempDirectory, "Veto test", new WritableImage(1, 1));
            var editor = new JavaLanguageSupport().open(project, DocumentId.create(), file).activeEditor();
            Services.BREAKPOINT_SERVICE.add(file, 2);
            editor.appendText("// draft");
            return editor;
        });
        Platform.runLater(open);
        TextEditorPane editor = open.get(10, TimeUnit.SECONDS);
        try {
            Files.delete(file);
            onFxThread(() -> {
                editor.close();
                assertFalse(editor.isEditorClosed());
                editor.insertText(0, "// inserted\n");
                assertTrue(Services.BREAKPOINT_SERVICE.has(file, 3));
                assertFalse(Services.BREAKPOINT_SERVICE.has(file, 2));
            });
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                Services.BREAKPOINT_SERVICE.remove(file, 3);
                Services.BREAKPOINT_SERVICE.remove(file, 2);
            });
        }
    }

    @Test
    public void disjointBatchPreservesInterveningStylesFoldsAndBreakpointThroughUndoRedo() throws Exception {
        Path file = tempDirectory.resolve("granular.txt");
        String original = "head\nfind\nuntouched\nfind\ntail";
        String replaced = "head\nfind\nextra\nuntouched\nfound\ntail!";
        Files.writeString(file, original);
        onFxThread(() -> {
            var editor = new TextEditorPane(DocumentId.create(), file, "plaintext");
            var service = new BreakpointService();
            var binding = new BreakpointGutterBinding(editor.getGutter(), editor, editor::getFilePath, service);
            try {
                var store = new DefaultDocumentStore("granular-test",
                    new WorkspaceConfigurationSnapshot(0, Set.of()));
                store.publishBacking(editor.captureSnapshot());
                editor.setDocumentStore(store);
                service.add(file, 3);
                editor.setParagraphStyle(2, List.of("debug-execution-line"));
                editor.setStyle(10, 19, List.of("custom-style"));
                editor.foldParagraphs(1, 2);
                var untouched = editor.getParagraph(2);
                editor.getUndoManager().forgetHistory();
                editor.appendText("!");
                var access = editor.textEditorAccess();
                assertEquals(EditResult.APPLIED, access.replace(access.snapshot(), List.of(
                    new TextReplacement(new TextRange(20, 24), "found"),
                    new TextReplacement(new TextRange(5, 9), "find\nextra"))));
                assertEquals(replaced, access.snapshot().text());
                assertEquals(untouched, editor.getParagraph(3));
                assertTrue(editor.isFolded(3));
                assertTrue(service.has(file, 4));
                assertFalse(service.has(file, 3));
                editor.appendText("?");
                editor.undo();
                assertEquals(replaced, editor.getText());
                editor.undo();
                assertEquals(original + "!", editor.getText());
                assertEquals(untouched, editor.getParagraph(2));
                assertTrue(editor.isFolded(2));
                assertTrue(service.has(file, 3));
                assertFalse(service.has(file, 4));
                editor.undo();
                assertEquals(original, editor.getText());
                editor.redo();
                editor.redo();
                assertEquals(replaced, editor.getText());
                assertEquals(untouched, editor.getParagraph(3));
                assertTrue(editor.isFolded(3));
                assertTrue(service.has(file, 4));
                assertFalse(service.has(file, 3));
                editor.redo();
                assertEquals(replaced + "?", access.snapshot().text());
            } finally {
                binding.close();
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            }
        });
    }

    @Test
    public void checkedBatchUsesOriginalOffsetsAndIsolatesUndoFromSurroundingTyping() throws Exception {
        Path file = tempDirectory.resolve("replace.txt");
        Files.writeString(file, "one two one");
        onFxThread(() -> {
            var editor = new TextEditorPane(DocumentId.create(), file, "plaintext");
            try {
                var store = new DefaultDocumentStore("replacement-test",
                    new WorkspaceConfigurationSnapshot(0, Set.of()));
                store.publishBacking(editor.captureSnapshot());
                editor.setDocumentStore(store);
                TextEditorAccess access = editor.textEditorAccess();
                editor.getUndoManager().forgetHistory();
                editor.appendText("!");
                var before = access.snapshot();
                assertSame(store.current(before.id()).orElseThrow(), before);
                assertEquals(EditResult.APPLIED, access.replace(before, List.of(
                    new TextReplacement(new TextRange(8, 11), "three"),
                    new TextReplacement(new TextRange(0, 3), "1"))));
                assertEquals("1 two three!", editor.getText());
                assertEquals(EditResult.STALE, access.replace(before, List.of(
                    new TextReplacement(new TextRange(0, 3), "stale"))));
                editor.appendText("?");
                editor.undo();
                assertEquals("1 two three!", editor.getText());
                editor.undo();
                assertEquals("one two one!", editor.getText());
                editor.undo();
                assertEquals("one two one", editor.getText());
                editor.redo();
                assertEquals("one two one!", editor.getText());
                editor.redo();
                assertEquals("1 two three!", editor.getText());
                editor.redo();
                assertEquals("1 two three!?", access.snapshot().text());
            } finally {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            }
        });
    }
}
