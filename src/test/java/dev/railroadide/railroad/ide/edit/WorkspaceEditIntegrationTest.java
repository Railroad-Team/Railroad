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
import javafx.application.Platform;
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

    private EditorTab openInTabPane(Path file) throws ReflectiveOperationException {
        Class<?> requestType = Class.forName(EditorTabManager.class.getName() + "$TabOpenRequest");
        var normalRequest = requestType.getDeclaredMethod("normal");
        normalRequest.setAccessible(true);
        var open = EditorTabManager.class.getDeclaredMethod("openInTabPane", Project.class, Path.class,
            DetachableTabPane.class, requestType);
        open.setAccessible(true);
        return (EditorTab) open.invoke(Services.EDITOR_TAB_MANAGER, project, file, new DetachableTabPane(),
            normalRequest.invoke(null));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
