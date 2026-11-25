package dev.railroadide.railroad.ide.edit;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.ide.ui.editor.EditorTabManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorPane;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import javafx.scene.control.Label;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceEditIntegrationTest {
    @TempDir
    public Path projectDirectory;
    private Project project;

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
        Field hostServices = Railroad.class.getDeclaredField("hostServices");
        hostServices.setAccessible(true);
        if (hostServices.get(null) == null) {
            hostServices.set(null, new Railroad().getHostServices());
        }
    }

    @BeforeEach
    public void openProject() throws Exception {
        onFx(() -> {
            LanguageSupports.initialize();
            if (ThemeManager.getTheme() == null) {
                ThemeManager.setTheme("");
            }

            project = new RailroadProject(projectDirectory, "Workspace edits", new WritableImage(1, 1));
            Services.IDE_STATE.setCurrentProject(project);
            return null;
        });

        Services.IDE_STATE.getWorkspaceConfiguration().ready().get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    public void closeProject() throws Exception {
        onFx(() -> {
            Services.EDITOR_TAB_MANAGER.discardUnsavedChangesOnClose();
            Services.EDITOR_TAB_MANAGER.prepareProjectClose();
            Railroad.EVENT_BUS.publish(new ProjectEvent(project, ProjectEvent.EventType.CLOSED));
            return null;
        });

        assertTrue(Services.IDE_STATE.getWorkspaceEdits().isEmpty());
    }

    @Test
    public void projectServiceRenamesAnOpenTabAndUndoesThroughTheEditor() throws Exception {
        Path original = projectDirectory.resolve("Original.txt");
        Files.writeString(original, "class Original {}");
        EditorTab tab = onFx(() -> openInTabPane(original));
        TextEditorPane editor = tab.view().activeEditor();
        WorkspaceEditService service = Services.IDE_STATE.getWorkspaceEdits().orElseThrow();
        Path renamed = projectDirectory.resolve("Renamed.txt").toAbsolutePath().normalize();

        WorkspaceEditResult result = onFx(() -> {
            TextDocumentSnapshot snapshot = editor.textEditorAccess().snapshot();
            return service.apply(WorkspaceEdit.builder("Rename")
                .replace(snapshot, new TextReplacement(new TextRange(6, 14), "Renamed"))
                .rename(snapshot, DocumentUri.fromPath(renamed))
                .build());
        });

        assertTrue(result.applied(), result.problems()::toString);
        assertEquals(renamed, tab.path());
        assertSame(editor, Services.EDITOR_TAB_MANAGER.openTextEditor(tab.documentId()).orElseThrow());
        assertEquals("class Renamed {}", onFx(editor::getText));
        assertEquals(tab.documentId(),
            Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(renamed)).orElseThrow().id());
        assertTrue(Files.exists(renamed));
        assertFalse(Files.exists(original));

        onFx(() -> {
            editor.undo();
            return null;
        });

        assertEquals(original.toAbsolutePath().normalize(), tab.path());
        assertEquals("class Original {}", onFx(editor::getText));
        assertTrue(Files.exists(original));
        assertFalse(Files.exists(renamed));
        DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
        assertEquals(DocumentUri.fromPath(original.toAbsolutePath().normalize()),
            store.current(tab.documentId()).orElseThrow().uri());
    }

    @Test
    public void undoSpansDocumentsAfterOpeningACrlfFileInATab() throws Exception {
        Path windows = projectDirectory.resolve("Windows.txt");
        Path other = projectDirectory.resolve("Other.txt");
        Files.writeString(windows, "first\r\nsecond\r\n");
        Files.writeString(other, "other");
        awaitFx(() -> published(windows) != null && published(other) != null);
        WorkspaceEditService service = Services.IDE_STATE.getWorkspaceEdits().orElseThrow();
        WorkspaceEditResult applied = onFx(() -> service.apply(WorkspaceEdit.builder("Edit")
            .replace(published(windows), new TextReplacement(new TextRange(0, 5), "FIRST"))
            .replace(published(other), new TextReplacement(new TextRange(0, 5), "OTHER"))
            .build()));
        assertTrue(applied.applied(), applied.problems()::toString);
        EditorTab tab = onFx(() -> openInTabPane(windows));
        TextEditorPane editor = tab.view().activeEditor();
        assertEquals("FIRST\nsecond\n", onFx(editor::getText));

        WorkspaceEditResult undone = onFx(service::undo);

        assertTrue(undone.applied(), undone.problems()::toString);
        assertEquals("first\nsecond\n", onFx(editor::getText));
        assertEquals("other", Files.readString(other));
        assertEquals(tab.documentId(), published(windows).id());
    }

    private static TextDocumentSnapshot published(Path path) {
        DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
        return Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(path))
            .flatMap(identity -> store.current(identity.id()))
            .filter(TextDocumentSnapshot.class::isInstance)
            .map(TextDocumentSnapshot.class::cast)
            .orElse(null);
    }

    @Test
    public void shiftF6RejectsUnsupportedTargetsAndPreviewsAppliesAndUndoesATypeRename() throws Exception {
        Path original = projectDirectory.resolve("Original.java");
        Path renamed = original.resolveSibling("Renamed.java");
        String source = "class Original<T> { Original<T> self; }";
        Files.writeString(original, source);
        DetachableTabPane tabs = onFx(DetachableTabPane::new);
        Stage stage = onFx(() -> {
            Platform.setImplicitExit(false);
            ThemeManager.init();
            Keybinds.initialize();
            var window = new Stage();
            window.setScene(new Scene(tabs, 640, 480));
            window.show();
            return window;
        });

        try {
            EditorTab tab = onFx(() -> openInTabPane(original, tabs));
            CodeEditorPane editor = (CodeEditorPane) tab.view().activeEditor();
            onFx(() -> {
                editor.moveTo(source.indexOf("T"));
                press(editor, KeyCode.F6, true);
                return null;
            });

            awaitFx(() -> visibleNode(".navigation-message") != null);

            onFx(() -> {
                editor.moveTo(source.indexOf("Original") + 1);
                press(editor, KeyCode.F6, true);
                return null;
            });

            awaitFx(() -> visibleNode(".rename-dialog") != null);
            TextField name = onFx(() -> (TextField) visibleNode(".rename-dialog").lookup(".text-field"));
            onFx(() -> {
                name.setText("class");
                press(name, KeyCode.ENTER, false);
                return null;
            });

            awaitFx(() -> visibleNode(".rename-error") != null);
            assertEquals(source, onFx(editor::getText));

            onFx(() -> {
                name.setText("Renamed");
                press(name, KeyCode.ENTER, false);
                return null;
            });

            awaitFx(() -> !((TreeView<?>) visibleNode(".rename-preview")).getRoot().getChildren().isEmpty());
            assertEquals(original, tab.path());
            assertFalse(Files.exists(renamed));
            onFx(() -> {
                press(name, KeyCode.ENTER, false);
                return null;
            });

            assertEquals(renamed, tab.path(), onFx(() -> {
                Label error = (Label) visibleNode(".rename-error");
                return error == null ? "" : error.getText();
            }));

            assertEquals(source.replace("Original", "Renamed"), onFx(editor::getText));
            assertTrue(Files.exists(renamed));
            assertFalse(Files.exists(original));
            onFx(() -> {
                editor.undo();
                return null;
            });

            assertEquals(original, tab.path());
            assertEquals(source, onFx(editor::getText));
            assertTrue(Files.exists(original));
            assertFalse(Files.exists(renamed));
        } finally {
            onFx(() -> {
                stage.close();
                return null;
            });
        }
    }

    private static void press(Node node, KeyCode code, boolean shift) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
    }

    private static Node visibleNode(String selector) {
        return Window.getWindows().stream().map(window -> window.getScene().lookup(selector))
            .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static void awaitFx(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!onFx(condition)) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for rename UI");
            Thread.sleep(20);
        }
    }

    private EditorTab openInTabPane(Path file) throws ReflectiveOperationException {
        return openInTabPane(file, new DetachableTabPane());
    }

    private EditorTab openInTabPane(Path file, DetachableTabPane tabs) throws ReflectiveOperationException {
        Class<?> requestType = Class.forName(EditorTabManager.class.getName() + "$TabOpenRequest");
        Method normalRequest = requestType.getDeclaredMethod("normal");
        normalRequest.setAccessible(true);
        Method open = EditorTabManager.class.getDeclaredMethod("openInTabPane", Project.class, Path.class,
            DetachableTabPane.class, requestType);
        open.setAccessible(true);
        return (EditorTab) open.invoke(Services.EDITOR_TAB_MANAGER, project, file, tabs,
            normalRequest.invoke(null));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
