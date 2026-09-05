package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedTab;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;

import java.util.Objects;

/** A localized tab backed by a stable {@link IDEDockItem} descriptor. */
public final class IDEDockTab extends LocalizedTab {
    private final IDEDockItem dockItem;

    /**
     * Creates a tool tab with its content initialization policy and workspace actions.
     *
     * @param dockItem tool pane whose workspace state is being queried or changed
     * @param project project whose files and workspace are being displayed
     * @param workspaceActions workspace navigation and tool-window actions
     */
    public IDEDockTab(IDEDockItem dockItem, Project project, IDEWorkspaceActions workspaceActions) {
        super(Objects.requireNonNull(dockItem, "Dock item cannot be null").localizationKey());
        this.dockItem = dockItem;
        setId(dockItem.id());
        setClosable(false);
        setContextMenu(createContextMenu(Objects.requireNonNull(workspaceActions, "Workspace actions cannot be null")));

        if (dockItem.initializationPolicy() == IDEDockItem.InitializationPolicy.ON_FIRST_SELECTION) {
            setOnSelectionChanged(_ -> {
                if (isSelected() && getContent() == null) {
                    setContent(dockItem.createContent(project));
                    setOnSelectionChanged(null);
                }
            });
        } else {
            setContent(dockItem.createContent(project));
        }
    }

    /**
     * Returns the tool definition represented by this tab.
     *
     * @return dock item definition
     */
    public IDEDockItem getDockItem() {
        return dockItem;
    }

    private ContextMenu createContextMenu(IDEWorkspaceActions workspaceActions) {
        var showHide = new LocalizedMenuItem("tool.tab.contextmenu.hide");
        showHide.setOnAction(_ -> workspaceActions.toggleDockItem(dockItem));

        var detach = new LocalizedMenuItem("tool.tab.contextmenu.detach");
        detach.setOnAction(_ -> workspaceActions.detachDockItem(dockItem));

        var resetPosition = new LocalizedMenuItem("tool.tab.contextmenu.reset_position");
        resetPosition.setOnAction(_ -> workspaceActions.resetDockItemPosition(dockItem));

        var contextMenu = new ContextMenu(showHide, new SeparatorMenuItem(), detach, resetPosition);
        contextMenu.setOnShowing(_ -> {
            showHide.setKey(workspaceActions.isDockItemActive(dockItem)
                ? "tool.tab.contextmenu.hide"
                : "tool.tab.contextmenu.show");
            boolean detached = workspaceActions.isDockItemDetached(dockItem);
            detach.setDisable(detached);
            resetPosition.setDisable(!detached);
        });
        return contextMenu;
    }
}
