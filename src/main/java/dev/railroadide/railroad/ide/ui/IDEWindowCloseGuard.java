package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.ui.editor.EditorTabManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Objects;
import java.util.stream.Collectors;

/** Flushes editor autosaves and protects failed documents when the IDE window closes. */
public final class IDEWindowCloseGuard implements AutoCloseable {
    private final Node owner;
    private final EventHandler<WindowEvent> closeRequestHandler = this::handleCloseRequest;
    private final ChangeListener<Scene> sceneListener = (_, oldScene, newScene) -> changeScene(oldScene, newScene);
    private final ChangeListener<Window> windowListener = (_, oldWindow, newWindow) -> changeWindow(oldWindow,
        newWindow);

    private Scene observedScene;
    private Stage observedStage;
    private Stage saveDialog;
    private Label dialogContent;
    private boolean allowClose;

    public IDEWindowCloseGuard(Node owner) {
        this.owner = Objects.requireNonNull(owner, "Owner cannot be null");
        owner.sceneProperty().addListener(sceneListener);
        changeScene(null, owner.getScene());
    }

    private void changeScene(Scene oldScene, Scene newScene) {
        if (oldScene != null) {
            oldScene.windowProperty().removeListener(windowListener);
        }
        changeWindow(oldScene == null ? null : oldScene.getWindow(), newScene == null ? null : newScene.getWindow());
        observedScene = newScene;
        if (newScene != null) {
            newScene.windowProperty().addListener(windowListener);
        }
    }

    private void changeWindow(Window oldWindow, Window newWindow) {
        if (oldWindow instanceof Stage oldStage) {
            oldStage.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
        }
        observedStage = newWindow instanceof Stage stage ? stage : null;
        if (observedStage != null) {
            observedStage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
        }
    }

    private void handleCloseRequest(WindowEvent event) {
        if (allowClose)
            return;

        EditorTabManager.SaveResult saveResult = Services.EDITOR_TAB_MANAGER.saveAll();
        if (saveResult.successful()) {
            closeActiveProject();
            return;
        }

        event.consume();
        showSaveDialog(saveResult);
    }

    private void showSaveDialog(EditorTabManager.SaveResult saveResult) {
        if (saveDialog != null) {
            updateDialogContent(saveResult);
            saveDialog.toFront();
            saveDialog.requestFocus();
            return;
        }

        dialogContent = new Label();
        dialogContent.setWrapText(true);
        dialogContent.setMaxWidth(560);
        updateDialogContent(saveResult);

        var saveButton = new RRButton("railroad.generic.save");
        saveButton.setVariant(ButtonVariant.PRIMARY);
        saveButton.setDefaultButton(true);
        var discardButton = new RRButton("railroad.generic.discard");
        discardButton.setVariant(ButtonVariant.DANGER);
        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);

        saveDialog = WindowBuilder.createDialog(
            "railroad.ide.close_unsaved.window_title",
            new DialogBuilder()
                .title("railroad.ide.close_unsaved.title")
                .contentNode(dialogContent)
                .buttons(saveButton, discardButton, cancelButton));
        Stage currentDialog = saveDialog;
        currentDialog.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ -> {
            if (saveDialog == currentDialog) {
                saveDialog = null;
                dialogContent = null;
            }
        });

        saveButton.setOnAction(_ -> {
            EditorTabManager.SaveResult retryResult = Services.EDITOR_TAB_MANAGER.saveAll();
            if (retryResult.successful()) {
                permitAndClose(currentDialog);
            } else {
                updateDialogContent(retryResult);
            }
        });
        discardButton.setOnAction(_ -> {
            Services.EDITOR_TAB_MANAGER.discardUnsavedChangesOnClose();
            permitAndClose(currentDialog);
        });
        cancelButton.setOnAction(_ -> currentDialog.close());
    }

    private void updateDialogContent(EditorTabManager.SaveResult saveResult) {
        if (dialogContent == null)
            return;

        String paths = saveResult.failedTabs().stream()
            .map(tab -> tab.path().toString())
            .collect(Collectors.joining(System.lineSeparator()));
        dialogContent.setText(L18n.localize("railroad.ide.close_unsaved.content", paths));
    }

    private void permitAndClose(Stage dialog) {
        closeActiveProject();
        allowClose = true;
        dialog.close();
        if (observedStage != null) {
            observedStage.close();
        }
    }

    private static void closeActiveProject() {
        Project project = Railroad.PROJECT_MANAGER.getOpenProject();
        if (project != null) {
            project.close();
        }
    }

    @Override
    public void close() {
        owner.sceneProperty().removeListener(sceneListener);
        if (observedScene != null) {
            observedScene.windowProperty().removeListener(windowListener);
        }
        if (observedStage != null) {
            observedStage.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequestHandler);
        }
        if (saveDialog != null) {
            saveDialog.close();
            saveDialog = null;
        }
        observedScene = null;
        observedStage = null;
        dialogContent = null;
    }
}
