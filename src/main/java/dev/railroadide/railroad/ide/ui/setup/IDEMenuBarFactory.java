package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.command.*;
import dev.railroadide.railroad.ide.WorkspaceMode;
import dev.railroadide.railroad.ide.WorkspaceModeController;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.ide.ui.IDEWorkspaceActions;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.settings.keybinds.Keybind;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRMenuBar;
import dev.railroadide.railroad.ui.localized.LocalizedCheckMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedMenu;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedRadioMenuItem;
import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.vcs.git.GitRepositoryState;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Consumer;

/**
 * Builds the main IDE menu bar with all menu items, accelerators, and icons.
 */
public final class IDEMenuBarFactory {
    private IDEMenuBarFactory() {
    }

    /**
     * Builds the project menu bar with editor, workspace, and tool actions.
     *
     * @param project project whose files and workspace are being displayed
     * @param viewModeController controller exposing workspace mode state and availability
     * @param viewModeRequester callback for requesting a workspace mode change
     * @param workspaceActions workspace navigation and tool-window actions
     * @return configured IDE menu bar
     */
    public static MenuBar create(
        Project project,
        WorkspaceModeController viewModeController,
        Consumer<WorkspaceMode> viewModeRequester,
        IDEWorkspaceActions workspaceActions
    ) {
        var menuBar = new RRMenuBar(true);

        var newFileItem = CommandMenuItems.create(Commands.NEW_FILE,
            () -> CommandContext.forProject(project, menuBar));
        newFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FILE));

        var openFileItem = CommandMenuItems.create(Commands.OPEN_FILE,
            () -> CommandContext.forProject(project, menuBar));
        openFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));

        var openProjectItem = CommandMenuItems.create(Commands.OPEN_PROJECT,
            () -> CommandContext.forProject(project, menuBar));
        openProjectItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));

        var recentProjects = new LocalizedMenu("railroad.menu.file.recent_projects");
        recentProjects.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        recentProjects.getItems().addAll(Railroad.PROJECT_MANAGER.getProjects().stream()
            .sorted(Comparator.comparingLong(Project::getLastOpened).reversed())
            .limit(5)
            .map(recentProject -> {
                var menuItem = new MenuItem(recentProject.getAlias());
                CommandMenuItems.bind(menuItem, Commands.OPEN_RECENT_PROJECT,
                    () -> CommandContext.withArgument(project, menuBar, recentProject));
                return menuItem;
            }).toList());

        var saveItem = CommandMenuItems.create(Commands.SAVE,
            () -> CommandContext.forProject(project, menuBar));
        saveItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));

        var saveAsItem = CommandMenuItems.create(Commands.SAVE_AS,
            () -> CommandContext.forProject(project, menuBar));
        saveAsItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));

        var saveAllItem = CommandMenuItems.create(Commands.SAVE_ALL,
            () -> CommandContext.forProject(project, menuBar));
        saveAllItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));

        var exitItem = CommandMenuItems.create(Commands.EXIT,
            () -> CommandContext.forProject(project, menuBar));
        exitItem.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));

        var undoItem = CommandMenuItems.create(EditCommands.UNDO, () -> CommandContext.forProject(project, menuBar));
        undoItem.setGraphic(new FontIcon(FontAwesomeSolid.UNDO));

        var redoItem = CommandMenuItems.create(EditCommands.REDO, () -> CommandContext.forProject(project, menuBar));
        redoItem.setGraphic(new FontIcon(FontAwesomeSolid.REDO));

        var cutItem = CommandMenuItems.create(EditCommands.CUT, () -> CommandContext.forProject(project, menuBar));
        cutItem.setGraphic(new FontIcon(FontAwesomeSolid.CUT));

        var copyItem = CommandMenuItems.create(EditCommands.COPY, () -> CommandContext.forProject(project, menuBar));
        copyItem.setGraphic(new FontIcon(FontAwesomeSolid.COPY));

        var pasteItem = CommandMenuItems.create(EditCommands.PASTE, () -> CommandContext.forProject(project, menuBar));
        pasteItem.setGraphic(new FontIcon(FontAwesomeSolid.PASTE));

        var findItem = new LocalizedMenuItem("railroad.menu.edit.find");
        findItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH));

        var replaceItem = new LocalizedMenuItem("railroad.menu.edit.replace");
        replaceItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH_PLUS));

        var toolWindowsMenu = createToolWindowsMenu(project, menuBar, workspaceActions);

        var navigateBackItem = CommandMenuItems.create(Commands.NAVIGATE_BACK,
            () -> CommandContext.withArgument(project, menuBar, workspaceActions));
        navigateBackItem.setGraphic(new FontIcon(FontAwesomeSolid.ARROW_LEFT));

        var navigateForwardItem = CommandMenuItems.create(Commands.NAVIGATE_FORWARD,
            () -> CommandContext.withArgument(project, menuBar, workspaceActions));
        navigateForwardItem.setGraphic(new FontIcon(FontAwesomeSolid.ARROW_RIGHT));

        var resetCurrentLayoutItem = CommandMenuItems.create(Commands.RESET_CURRENT_LAYOUT,
            () -> CommandContext.withArgument(project, menuBar, workspaceActions));
        resetCurrentLayoutItem.setGraphic(new FontIcon(FontAwesomeSolid.UNDO));

        var resetAllLayoutsItem = CommandMenuItems.create(Commands.RESET_ALL_LAYOUTS,
            () -> CommandContext.withArgument(project, menuBar, workspaceActions));
        resetAllLayoutsItem.setGraphic(new FontIcon(FontAwesomeSolid.HISTORY));

        var fullScreenItem = CommandMenuItems.create(Commands.FULLSCREEN,
            () -> CommandContext.forProject(project, menuBar));
        fullScreenItem.setGraphic(new FontIcon(FontAwesomeSolid.EXPAND));

        var viewModeToggleGroup = new ToggleGroup();
        Map<WorkspaceMode, LocalizedRadioMenuItem> viewModeItems = new LinkedHashMap<>();
        for (WorkspaceMode viewMode : WorkspaceMode.REGISTRY.values()) {
            var item = new LocalizedRadioMenuItem(viewMode.getLocalizationKey());
            item.setToggleGroup(viewModeToggleGroup);
            if (viewMode.getGraphic() != null) {
                item.setGraphic(new FontIcon(viewMode.getGraphic()));
            }
            var command = Commands.viewMode(viewMode);
            if (command != null) {
                CommandMenuItems.bind(item, command, () -> CommandContext.forProject(project, menuBar));
            } else {
                CommandMenuItems.bind(item, ApplicationCommands.WORKSPACE_MODE,
                    () -> CommandContext.withArgument(project, menuBar,
                        new ApplicationCommands.ModeRequest(viewMode, viewModeRequester)));
                Keybind accelerator = KeybindHandler.getKeybind(viewMode.getAcceleratorId());
                if (accelerator != null) {
                    bindConfiguredAccelerator(item, accelerator);
                }
                ObservableBooleanValue unavailable = viewMode.createUnavailableBinding(project);
                if (unavailable != null) {
                    item.disableProperty().bind(unavailable);
                }
            }
            viewModeItems.put(viewMode, item);
        }

        var gitDetectingItem = new LocalizedMenuItem("railroad.ide.view_mode.git_detecting");
        gitDetectingItem.setDisable(true);
        gitDetectingItem.visibleProperty().bind(project.getGitManager().repositoryStateProperty()
            .isEqualTo(GitRepositoryState.DETECTING));

        var gitUnavailableItem = new LocalizedMenuItem("railroad.ide.view_mode.git_unavailable");
        gitUnavailableItem.setDisable(true);
        gitUnavailableItem.visibleProperty().bind(project.getGitManager().repositoryStateProperty()
            .isEqualTo(GitRepositoryState.UNAVAILABLE));

        var gitDetectionFailedItem = new LocalizedMenuItem("railroad.ide.view_mode.git_detection_failed");
        gitDetectionFailedItem.visibleProperty().bind(project.getGitManager().repositoryStateProperty()
            .isEqualTo(GitRepositoryState.FAILED));
        CommandMenuItems.bind(gitDetectionFailedItem, Commands.RETRY_GIT_DETECTION,
            () -> CommandContext.forProject(project, menuBar));

        viewModeController.onViewModeChanged(viewMode -> viewModeToggleGroup.selectToggle(viewModeItems.get(viewMode)));

        var viewModeMenu = new LocalizedMenu("railroad.menu.view.mode");
        viewModeMenu.getItems().addAll(viewModeItems.values());
        viewModeMenu.getItems().addAll(
            gitDetectingItem,
            gitUnavailableItem,
            gitDetectionFailedItem);

        var runItem = CommandMenuItems.create(RunCommands.RUN, () -> CommandContext.forProject(project, menuBar));
        runItem.setGraphic(new FontIcon(FontAwesomeSolid.PLAY));

        var debugItem = CommandMenuItems.create(RunCommands.DEBUG, () -> CommandContext.forProject(project, menuBar));
        debugItem.setGraphic(new FontIcon(FontAwesomeSolid.BUG));

        var stopItem = CommandMenuItems.create(RunCommands.STOP, () -> CommandContext.forProject(project, menuBar));
        stopItem.setGraphic(new FontIcon(FontAwesomeSolid.STOP));

        var settingsItem = CommandMenuItems.create(Commands.OPEN_SETTINGS,
            () -> CommandContext.forProject(project, menuBar));
        settingsItem.setGraphic(new FontIcon(FontAwesomeSolid.COG));

        var pluginsItem = CommandMenuItems.create(Commands.OPEN_PLUGINS,
            () -> CommandContext.forProject(project, menuBar));
        pluginsItem.setGraphic(new FontIcon(FontAwesomeSolid.PUZZLE_PIECE));

        var terminalItem = CommandMenuItems.create(Commands.toggleDockItem(IDEDockItem.TERMINAL),
            () -> CommandContext.withArgument(project, menuBar, workspaceActions));
        terminalItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));

        var fileMenu = new LocalizedMenu("railroad.menu.file");
        fileMenu.getItems().addAll(newFileItem, openFileItem, openProjectItem, recentProjects, saveItem, saveAsItem,
            saveAllItem, new SeparatorMenuItem(), exitItem);
        fileMenu.getStyleClass().add("rr-menu");

        var editMenu = new LocalizedMenu("railroad.menu.edit");
        editMenu.getItems().addAll(
            undoItem,
            redoItem,
            new SeparatorMenuItem(),
            cutItem,
            copyItem,
            pasteItem,
            new SeparatorMenuItem(),
            findItem,
            replaceItem);
        editMenu.getStyleClass().add("rr-menu");

        var viewMenu = new LocalizedMenu("railroad.menu.view");
        viewMenu.getItems().addAll(
            navigateBackItem,
            navigateForwardItem,
            new SeparatorMenuItem(),
            viewModeMenu,
            new SeparatorMenuItem(),
            toolWindowsMenu,
            resetCurrentLayoutItem,
            resetAllLayoutsItem,
            new SeparatorMenuItem(),
            fullScreenItem);
        viewMenu.getStyleClass().add("rr-menu");

        var runMenu = new LocalizedMenu("railroad.menu.run");
        runMenu.getItems().addAll(runItem, debugItem, stopItem);
        runMenu.getStyleClass().add("rr-menu");

        var toolsMenu = new LocalizedMenu("railroad.menu.tools");
        toolsMenu.getItems().addAll(settingsItem, pluginsItem, terminalItem);
        toolsMenu.getStyleClass().add("rr-menu");

        menuBar.getMenus().addAll(0, List.of(fileMenu, editMenu, viewMenu, runMenu, toolsMenu));
        if (OperatingSystem.isMac()) {
            menuBar.useSystemMenuBarProperty().set(true);
        }
        menuBar.getStyleClass().add("rr-menu-bar");
        return menuBar;
    }

    private static LocalizedMenu createToolWindowsMenu(
        Project project,
        MenuBar menuBar,
        IDEWorkspaceActions workspaceActions
    ) {
        var toolWindowsMenu = new LocalizedMenu("railroad.menu.view.tool_windows");
        Map<IDEDockItem, LocalizedCheckMenuItem> menuItems = new EnumMap<>(IDEDockItem.class);
        IDEDockItem.DockPosition previousPosition = null;

        for (IDEDockItem dockItem : IDEDockItem.values()) {
            if (previousPosition != null && previousPosition != dockItem.preferredDockPosition()) {
                toolWindowsMenu.getItems().add(new SeparatorMenuItem());
            }

            var menuItem = new LocalizedCheckMenuItem(dockItem.localizationKey(), false);
            menuItem.setGraphic(new FontIcon(dockItem.icon()));
            CommandMenuItems.bind(menuItem, Commands.toggleDockItem(dockItem),
                () -> CommandContext.withArgument(project, menuBar, workspaceActions));
            menuItems.put(dockItem, menuItem);
            toolWindowsMenu.getItems().add(menuItem);
            previousPosition = dockItem.preferredDockPosition();
        }

        toolWindowsMenu.setOnShowing(_ -> menuItems.forEach((dockItem, menuItem) -> {
            menuItem.setSelected(workspaceActions.isDockItemActive(dockItem));
        }));
        return toolWindowsMenu;
    }

    private static void bindConfiguredAccelerator(MenuItem menuItem, Keybind keybind) {
        ListChangeListener<KeybindData> listener = _ -> updateConfiguredAccelerator(menuItem, keybind);
        keybind.getKeys().addListener(new WeakListChangeListener<>(listener));
        menuItem.getProperties().put("railroad:keybind-listener", listener);
        updateConfiguredAccelerator(menuItem, keybind);
    }

    private static void updateConfiguredAccelerator(MenuItem menuItem, Keybind keybind) {
        keybind.getKeys().stream()
            .filter(keybindData -> keybindData.keyCode() != null && keybindData.keyCode() != KeyCode.UNDEFINED)
            .findFirst()
            .map(KeybindData::getKeyCodeCombination)
            .ifPresentOrElse(menuItem::setAccelerator, () -> menuItem.setAccelerator(null));
    }

}
