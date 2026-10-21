package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.ui.editor.EditorSaveState;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    @Test
    public void capturesStableIdentityAndVersionUntilTextChanges() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String editedText = "class Example { int value; }";
        Files.writeString(file, originalText);
        var openEditor = new FutureTask<>(() -> new TextEditorPane(file, "java"));
        Platform.runLater(openEditor);
        TextEditorPane editor = openEditor.get(10, TimeUnit.SECONDS);

        try {
            onFxThread(() -> {
                var original = editor.captureSnapshot();
                var unchanged = editor.captureSnapshot();

                assertEquals(originalText, original.text());
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
        var openEditor = new FutureTask<>(() -> new TextEditorPane(file, "java"));
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
        var openEditor = new FutureTask<>(() -> new TextEditorPane(file, "java"));
        Platform.runLater(openEditor);
        TextEditorPane editor = openEditor.get(10, TimeUnit.SECONDS);
        var reloaded = new CompletableFuture<Void>();
        ChangeListener<EditorSaveState> reloadListener = (observable, oldState, newState) -> {
            if (newState == EditorSaveState.CLEAN && updatedText.equals(editor.getText())) {
                reloaded.complete(null);
            }
        };

        try {
            var captureOriginal = new FutureTask<>(() -> {
                editor.saveStateProperty().addListener(reloadListener);
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
                editor.saveStateProperty().removeListener(reloadListener);
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
}
