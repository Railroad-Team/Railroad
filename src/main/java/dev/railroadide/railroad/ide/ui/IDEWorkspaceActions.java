package dev.railroadide.railroad.ide.ui;

/** Actions exposed by the current IDE workspace to menus and other navigation controls. */
public interface IDEWorkspaceActions {
    boolean isDockItemAvailable(IDEDockItem dockItem);

    boolean isDockItemActive(IDEDockItem dockItem);

    void toggleDockItem(IDEDockItem dockItem);

    void resetCurrentLayout();

    void resetAllLayouts();
}
