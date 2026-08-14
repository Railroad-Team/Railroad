package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ide.IDEViewModeController;
import dev.railroadide.railroad.ui.id.UIId;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.application.Platform;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Routes editor content to a stable, mode-specific dock and activates the owning view mode.
 */
public final class IDEContentRouter {
    private final IDEViewModeController viewModeController;

    IDEContentRouter(IDEViewModeController viewModeController) {
        this.viewModeController = Objects.requireNonNull(viewModeController, "View mode controller cannot be null");
    }

    /**
     * Routes content through the currently attached IDE pane.
     *
     * @param target destination editor dock
     * @param action content-opening action to perform with that dock
     */
    public static void routeActive(Target target, Consumer<DetachableTabPane> action) {
        Objects.requireNonNull(target, "Content target cannot be null");
        Objects.requireNonNull(action, "Content action cannot be null");

        runOnApplicationThread(() -> Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
            .map(IDEPane::getContentRouter)
            .ifPresent(router -> router.routeOnApplicationThread(target, action)));
    }

    public void route(Target target, Consumer<DetachableTabPane> action) {
        Objects.requireNonNull(target, "Content target cannot be null");
        Objects.requireNonNull(action, "Content action cannot be null");
        runOnApplicationThread(() -> routeOnApplicationThread(target, action));
    }

    private void routeOnApplicationThread(Target target, Consumer<DetachableTabPane> action) {
        viewModeController.setCurrentViewMode(target.viewMode);
        Services.UI_MANAGER.lookup(target.dockId).ifPresent(action);
    }

    private static void runOnApplicationThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public enum Target {
        CODE_EDITOR(IDEViewMode.CODE, UIIds.IDE.IDE_CODE_EDITOR_DOCK),
        GIT_EDITOR(IDEViewMode.GIT, UIIds.IDE.IDE_GIT_EDITOR_DOCK);

        private final IDEViewMode viewMode;
        private final UIId<DetachableTabPane> dockId;

        Target(IDEViewMode viewMode, UIId<DetachableTabPane> dockId) {
            this.viewMode = viewMode;
            this.dockId = dockId;
        }
    }
}
