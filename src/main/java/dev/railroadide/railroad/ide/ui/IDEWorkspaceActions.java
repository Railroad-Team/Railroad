package dev.railroadide.railroad.ide.ui;

/** Actions exposed by the current IDE workspace to menus and other navigation controls. */
public interface IDEWorkspaceActions {
    boolean canNavigateBack();

    boolean canNavigateForward();

    void navigateBack();

    void navigateForward();

    boolean isDockItemAvailable(IDEDockItem dockItem);

    boolean isDockItemActive(IDEDockItem dockItem);

    void toggleDockItem(IDEDockItem dockItem);

    boolean isDockItemDetached(IDEDockItem dockItem);

    void detachDockItem(IDEDockItem dockItem);

    void resetDockItemPosition(IDEDockItem dockItem);

    void resetCurrentLayout();

    void resetAllLayouts();
}
