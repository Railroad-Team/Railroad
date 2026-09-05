package dev.railroadide.railroad.ide.ui;

/** Actions exposed by the current IDE workspace to menus and other navigation controls. */
public interface IDEWorkspaceActions {
    /**
     * Checks whether an earlier editor navigation entry is available.
     *
     * @return true when backward navigation is possible
     */
    boolean canNavigateBack();

    /**
     * Checks whether a later editor navigation entry is available.
     *
     * @return true when forward navigation is possible
     */
    boolean canNavigateForward();

    /**
     * Navigates to the preceding available editor entry.
     */
    void navigateBack();

    /**
     * Navigates to the following available editor entry.
     */
    void navigateForward();

    /**
     * Checks whether the tool is available in the workspace.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     * @return true when the tool can be shown
     */
    boolean isDockItemAvailable(IDEDockItem dockItem);

    /**
     * Checks whether the tool is currently active.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     * @return true when the tool is active
     */
    boolean isDockItemActive(IDEDockItem dockItem);

    /**
     * Toggles visibility or selection of a workspace tool.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     */
    void toggleDockItem(IDEDockItem dockItem);

    /**
     * Checks whether the tool is hosted in a detached window.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     * @return true when the tool is detached
     */
    boolean isDockItemDetached(IDEDockItem dockItem);

    /**
     * Moves a workspace tool into its own window.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     */
    void detachDockItem(IDEDockItem dockItem);

    /**
     * Restores a tool to its default dock position.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     */
    void resetDockItemPosition(IDEDockItem dockItem);

    /**
     * Resets the active workspace mode to its default layout.
     */
    void resetCurrentLayout();

    /**
     * Resets every workspace mode to its default layout.
     */
    void resetAllLayouts();
}
