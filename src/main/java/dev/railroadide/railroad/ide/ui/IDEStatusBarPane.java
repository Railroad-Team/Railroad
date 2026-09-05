package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;

/**
 * Provides the registered status-bar container for the active IDE workspace.
 */
public class IDEStatusBarPane extends RRHBox {
    /**
     * Creates a status bar registered while attached to a scene.
     */
    public IDEStatusBarPane() {
        super();

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_STATUS_BAR, this);
    }
}
