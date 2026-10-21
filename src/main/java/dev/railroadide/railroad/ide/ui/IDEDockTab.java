package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandMenuItems;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedTab;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;

import java.util.Objects;

/** A localized tab backed by a stable {@link IDEDockItem} descriptor. */
public final class IDEDockTab extends LocalizedTab {
    private final IDEDockItem dockItem;
    private final Project project;
    private final IDEWorkspaceActions workspaceActions;

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
        this.project = project;
        this.workspaceActions = workspaceActions;
        setId(dockItem.id());
        setClosable(false);
        setContextMenu(createContextMenu(
            project,
            Objects.requireNonNull(workspaceActions, "Workspace actions cannot be null")));

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
     * Creates an invocation target for this tab’s workspace actions.
     *
     * @return project, content source, and workspace action target
     */
    public CommandContext<IDEWorkspaceActions> commandContext() {
        return CommandContext.withArgument(project, getContent(), workspaceActions);
    }

    /**
     * Returns the descriptor represented by this dock tab.
     *
     * @return dock item descriptor
     */
    public IDEDockItem getDockItem() {
        return dockItem;
    }

    private ContextMenu createContextMenu(Project project, IDEWorkspaceActions workspaceActions) {
        var showHide = new LocalizedMenuItem("tool.tab.contextmenu.hide");
        CommandMenuItems.bind(showHide, Commands.toggleDockItem(dockItem),
            () -> CommandContext.withArgument(project, getContent(), workspaceActions));

        var detach = new LocalizedMenuItem("tool.tab.contextmenu.detach");
        CommandMenuItems.bind(detach, Commands.detachDockItem(dockItem),
            () -> CommandContext.withArgument(project, getContent(), workspaceActions));

        var resetPosition = new LocalizedMenuItem("tool.tab.contextmenu.reset_position");
        CommandMenuItems.bind(resetPosition, Commands.resetDockItemPosition(dockItem),
            () -> CommandContext.withArgument(project, getContent(), workspaceActions));

        var contextMenu = new ContextMenu(showHide, new SeparatorMenuItem(), detach, resetPosition);
        contextMenu.setOnShowing(_ -> {
            showHide.setKey(workspaceActions.isDockItemActive(dockItem)
                ? "tool.tab.contextmenu.hide"
                : "tool.tab.contextmenu.show");
        });
        return contextMenu;
    }
}
