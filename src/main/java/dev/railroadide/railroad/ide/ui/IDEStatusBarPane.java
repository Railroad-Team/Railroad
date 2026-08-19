package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;

public class IDEStatusBarPane extends RRHBox {
    public IDEStatusBarPane() {
        super();

        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.IDE_STATUS_BAR, this);
    }
}
