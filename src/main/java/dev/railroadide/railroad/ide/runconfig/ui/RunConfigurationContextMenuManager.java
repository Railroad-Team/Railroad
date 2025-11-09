package dev.railroadide.railroad.ide.runconfig.ui;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

/**
 * Ensures run configuration context menus do not stack on top of each other by
 * hiding any existing menu before showing a new one.
 */
public final class RunConfigurationContextMenuManager {
    private static ContextMenu currentlyShown;

    private RunConfigurationContextMenuManager() {}

    public static synchronized void show(Node anchor, ContextMenu menu, Side side) {
        if (currentlyShown != null && currentlyShown != menu) {
            currentlyShown.hide();
        }

        currentlyShown = menu;
        var previousHiddenHandler = menu.getOnHidden();
        menu.setOnHidden(event -> {
            if (previousHiddenHandler != null) {
                previousHiddenHandler.handle(event);
            }

            synchronized (RunConfigurationContextMenuManager.class) {
                if (currentlyShown == menu) {
                    currentlyShown = null;
                }
            }
        });

        menu.show(anchor, side, 0, 0);
    }
}
