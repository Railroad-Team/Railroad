package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Routes editor content to a stable, mode-specific dock and activates the owning view mode.
 */
public final class IDEContentRouter {
    private final IDEPane idePane;

    /**
     * Creates a content router for one IDE workspace.
     *
     * @param idePane workspace that owns the target editor panes
     */
    public IDEContentRouter(IDEPane idePane) {
        this.idePane = Objects.requireNonNull(idePane, "IDE pane cannot be null");
    }

    /**
     * Routes content through the currently attached IDE pane.
     *
     * @param target destination editor dock
     * @param action content-opening action to perform with that dock
     */
    public static void routeActive(WorkspaceContentTarget target, Consumer<DetachableTabPane> action) {
        Objects.requireNonNull(target, "Content target cannot be null");
        Objects.requireNonNull(action, "Content action cannot be null");

        JavaFXUtils.runOnApplicationThread(() -> Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
            .map(IDEPane::getContentRouter)
            .ifPresent(router -> router.routeOnApplicationThread(target, action)));
    }

    /**
     * Requests the destination workspace mode and runs the content action on the JavaFX application thread.
     *
     * @param target registered workspace destination for the content
     * @param action operation to run when the destination mode is available and its pane is found
     */
    public void route(WorkspaceContentTarget target, Consumer<DetachableTabPane> action) {
        Objects.requireNonNull(target, "Content target cannot be null");
        Objects.requireNonNull(action, "Content action cannot be null");
        JavaFXUtils.runOnApplicationThread(() -> routeOnApplicationThread(target, action));
    }

    private void routeOnApplicationThread(WorkspaceContentTarget target, Consumer<DetachableTabPane> action) {
        if (idePane.requestViewMode(target.getMode())) {
            Services.UI_MANAGER.lookup(target.getDockId()).ifPresent(action);
        }
    }

}
