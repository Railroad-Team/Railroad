package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.RailroadProcessLauncher;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDEViewMode;
import dev.railroadide.railroad.ide.IDEViewModeController;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.ide.ui.IDEWorkspaceActions;
import dev.railroadide.railroad.ide.projectexplorer.FileCreateType;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.settings.keybinds.Keybind;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRMenuBar;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedMenu;
import dev.railroadide.railroad.ui.localized.LocalizedCheckMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ui.localized.LocalizedRadioMenuItem;
import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.vcs.git.GitRepositoryState;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroad.window.WindowManager;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds the main IDE menu bar with all menu items, accelerators, and icons.
 */
public final class IDEMenuBarFactory {
    private IDEMenuBarFactory() {
    }

    public static MenuBar create(
        Project project,
        IDEViewModeController viewModeController,
        Consumer<IDEViewMode> viewModeRequester,
        IDEWorkspaceActions workspaceActions
    ) {
        var newFileItem = new LocalizedMenuItem("railroad.menu.file.new_file");
        newFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FILE));
        newFileItem.setKeybindData(new KeybindData(KeyCode.N, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}));
        newFileItem.setOnAction(_ -> {
            Path directoryPath = Services.UI_MANAGER.lookup(UIIds.IDE.PROJECT_EXPLORER)
                .map(ProjectExplorerPane::getSelectedDirectory)
                .orElseGet(project::getPath);
            CreateFileDialog.open(Railroad.WINDOW_MANAGER.getPrimaryStage(), directoryPath, FileCreateType.FILE);
        });

        var openFileItem = new LocalizedMenuItem("railroad.menu.file.open_file");
        openFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        openFileItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));

        var recentProjects = new LocalizedMenu("railroad.menu.file.recent_projects");
        recentProjects.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        recentProjects.getItems().addAll(Railroad.PROJECT_MANAGER.getProjects().stream()
            .sorted(Comparator.comparingLong(Project::getLastOpened).reversed())
            .limit(5)
            .map(recentProject -> {
                var menuItem = new MenuItem(recentProject.getAlias());
                menuItem.setOnAction(_ -> showOpenProjectDialog(recentProject));
                return menuItem;
            }).toList());

        var saveItem = new LocalizedMenuItem("railroad.menu.file.save");
        saveItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));

        var saveAsItem = new LocalizedMenuItem("railroad.menu.file.save_as");
        saveAsItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));

        var exitItem = new LocalizedMenuItem("railroad.menu.file.exit");
        exitItem.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exitItem.setOnAction(_ -> Platform.exit());

        var undoItem = new LocalizedMenuItem("railroad.menu.edit.undo");
        undoItem.setGraphic(new FontIcon(FontAwesomeSolid.UNDO));
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));

        var redoItem = new LocalizedMenuItem("railroad.menu.edit.redo");
        redoItem.setGraphic(new FontIcon(FontAwesomeSolid.REDO));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));

        var cutItem = new LocalizedMenuItem("railroad.menu.edit.cut");
        cutItem.setGraphic(new FontIcon(FontAwesomeSolid.CUT));
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN));

        var copyItem = new LocalizedMenuItem("railroad.menu.edit.copy");
        copyItem.setGraphic(new FontIcon(FontAwesomeSolid.COPY));
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));

        var pasteItem = new LocalizedMenuItem("railroad.menu.edit.paste");
        pasteItem.setGraphic(new FontIcon(FontAwesomeSolid.PASTE));
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));

        var findItem = new LocalizedMenuItem("railroad.menu.edit.find");
        findItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH));
        findItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));

        var replaceItem = new LocalizedMenuItem("railroad.menu.edit.replace");
        replaceItem.setGraphic(new FontIcon(FontAwesomeSolid.SEARCH_PLUS));
        replaceItem.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN));

        var toolWindowsMenu = createToolWindowsMenu(workspaceActions);

        var resetCurrentLayoutItem = new LocalizedMenuItem("railroad.menu.view.reset_current_layout");
        resetCurrentLayoutItem.setGraphic(new FontIcon(FontAwesomeSolid.UNDO));
        resetCurrentLayoutItem.setOnAction(_ -> workspaceActions.resetCurrentLayout());

        var resetAllLayoutsItem = new LocalizedMenuItem("railroad.menu.view.reset_all_layouts");
        resetAllLayoutsItem.setGraphic(new FontIcon(FontAwesomeSolid.HISTORY));
        resetAllLayoutsItem.setOnAction(_ -> workspaceActions.resetAllLayouts());

        var fullScreenItem = new LocalizedMenuItem("railroad.menu.view.full_screen");
        fullScreenItem.setGraphic(new FontIcon(FontAwesomeSolid.EXPAND));
        fullScreenItem.setOnAction(_ -> WindowManager.toggleFullScreen());

        var viewModeToggleGroup = new ToggleGroup();

        var codeModeItem = new LocalizedRadioMenuItem("railroad.ide.view_mode.code");
        codeModeItem.setGraphic(new FontIcon(FontAwesomeSolid.CODE));
        codeModeItem.setToggleGroup(viewModeToggleGroup);
        codeModeItem.setOnAction(_ -> viewModeRequester.accept(IDEViewMode.CODE));
        bindConfiguredAccelerator(codeModeItem, Keybinds.VIEW_MODE_CODE);

        var gitModeItem = new LocalizedRadioMenuItem("railroad.ide.view_mode.git");
        gitModeItem.setGraphic(new FontIcon(FontAwesomeBrands.GIT_ALT));
        gitModeItem.setToggleGroup(viewModeToggleGroup);
        gitModeItem.setOnAction(_ -> viewModeRequester.accept(IDEViewMode.GIT));
        gitModeItem.disableProperty().bind(project.getGitManager().repositoryStateProperty()
            .isNotEqualTo(GitRepositoryState.AVAILABLE));
        bindConfiguredAccelerator(gitModeItem, Keybinds.VIEW_MODE_GIT);

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
        gitDetectionFailedItem.setOnAction(_ -> project.getGitManager().detectRepository());

        viewModeController.onViewModeChanged(viewMode -> viewModeToggleGroup.selectToggle(switch (viewMode) {
            case CODE -> codeModeItem;
            case GIT -> gitModeItem;
        }));

        var viewModeMenu = new LocalizedMenu("railroad.menu.view.mode");
        viewModeMenu.getItems().addAll(
            codeModeItem,
            gitModeItem,
            gitDetectingItem,
            gitUnavailableItem,
            gitDetectionFailedItem
        );

        var runItem = new LocalizedMenuItem("railroad.menu.run.run");
        runItem.setGraphic(new FontIcon(FontAwesomeSolid.PLAY));
        runItem.setAccelerator(new KeyCodeCombination(KeyCode.F5));

        var debugItem = new LocalizedMenuItem("railroad.menu.run.debug");
        debugItem.setGraphic(new FontIcon(FontAwesomeSolid.BUG));
        debugItem.setAccelerator(new KeyCodeCombination(KeyCode.F6));

        var stopItem = new LocalizedMenuItem("railroad.menu.run.stop");
        stopItem.setGraphic(new FontIcon(FontAwesomeSolid.STOP));
        stopItem.setAccelerator(new KeyCodeCombination(KeyCode.F7));

        var settingsItem = new LocalizedMenuItem("railroad.menu.tools.settings");
        settingsItem.setGraphic(new FontIcon(FontAwesomeSolid.COG));
        settingsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
        settingsItem.setOnAction(_ -> SettingsPane.openSettingsWindow());

        var pluginsItem = new LocalizedMenuItem("railroad.menu.tools.plugins");
        pluginsItem.setGraphic(new FontIcon(FontAwesomeSolid.PUZZLE_PIECE));
        pluginsItem.setOnAction(_ -> SettingsPane.openPluginsWindow());

        var terminalItem = new LocalizedMenuItem("railroad.menu.tools.terminal");
        terminalItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        terminalItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        terminalItem.setOnAction(_ -> workspaceActions.toggleDockItem(IDEDockItem.TERMINAL));

        var fileMenu = new LocalizedMenu("railroad.menu.file");
        fileMenu.getItems().addAll(newFileItem, openFileItem, recentProjects, saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);
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
            replaceItem
        );
        editMenu.getStyleClass().add("rr-menu");

        var viewMenu = new LocalizedMenu("railroad.menu.view");
        viewMenu.getItems().addAll(
            viewModeMenu,
            new SeparatorMenuItem(),
            toolWindowsMenu,
            resetCurrentLayoutItem,
            resetAllLayoutsItem,
            new SeparatorMenuItem(),
            fullScreenItem
        );
        viewMenu.getStyleClass().add("rr-menu");

        var runMenu = new LocalizedMenu("railroad.menu.run");
        runMenu.getItems().addAll(runItem, debugItem, stopItem);
        runMenu.getStyleClass().add("rr-menu");

        var toolsMenu = new LocalizedMenu("railroad.menu.tools");
        toolsMenu.getItems().addAll(settingsItem, pluginsItem, terminalItem);
        toolsMenu.getStyleClass().add("rr-menu");

        var menuBar = new RRMenuBar(true, fileMenu, editMenu, viewMenu, runMenu, toolsMenu);
        if (OperatingSystem.isMac()) {
            menuBar.useSystemMenuBarProperty().set(true);
        }
        menuBar.getStyleClass().add("rr-menu-bar");
        return menuBar;
    }

    private static LocalizedMenu createToolWindowsMenu(IDEWorkspaceActions workspaceActions) {
        var toolWindowsMenu = new LocalizedMenu("railroad.menu.view.tool_windows");
        Map<IDEDockItem, LocalizedCheckMenuItem> menuItems = new EnumMap<>(IDEDockItem.class);
        IDEDockItem.DockPosition previousPosition = null;

        for (IDEDockItem dockItem : IDEDockItem.values()) {
            if (previousPosition != null && previousPosition != dockItem.preferredDockPosition()) {
                toolWindowsMenu.getItems().add(new SeparatorMenuItem());
            }

            var menuItem = new LocalizedCheckMenuItem(dockItem.localizationKey(), false);
            menuItem.setGraphic(new FontIcon(dockItem.icon()));
            menuItem.setOnAction(_ -> {
                workspaceActions.toggleDockItem(dockItem);
                menuItem.setSelected(workspaceActions.isDockItemActive(dockItem));
            });
            menuItems.put(dockItem, menuItem);
            toolWindowsMenu.getItems().add(menuItem);
            previousPosition = dockItem.preferredDockPosition();
        }

        toolWindowsMenu.setOnShowing(_ -> menuItems.forEach((dockItem, menuItem) -> {
            menuItem.setDisable(!workspaceActions.isDockItemAvailable(dockItem));
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
            .filter(keybindData -> keybindData.keyCode() != KeyCode.UNDEFINED)
            .findFirst()
            .map(KeybindData::getKeyCodeCombination)
            .ifPresentOrElse(menuItem::setAccelerator, () -> menuItem.setAccelerator(null));
    }

    private static void showOpenProjectDialog(Project project) {
        var thisWindowButton = new RRButton("railroad.recent_projects.dialog.this_window_button");
        var newWindowButton = new RRButton("railroad.recent_projects.dialog.new_window_button");
        var cancelButton = new RRButton("railroad.recent_projects.dialog.cancel_button");

        Stage dialog = WindowBuilder.createDialog("railroad.recent_projects.dialog.title", new DialogBuilder()
            .title("railroad.recent_projects.dialog.title")
            .content(L18n.localize("railroad.recent_projects.dialog.description", project.getAlias()), false)
            .buttons(thisWindowButton, newWindowButton, cancelButton));

        thisWindowButton.setOnAction(_ -> {
            dialog.close();
            project.open(Railroad.WINDOW_MANAGER.getPrimaryStage());
        });

        newWindowButton.setOnAction(_ -> {
            try {
                RailroadProcessLauncher.openProject(project.getPath());
                dialog.close();
            } catch (IOException exception) {
                Railroad.LOGGER.error("An error occurred trying to start a new Railroad process", exception);
            }
        });

        cancelButton.setOnAction(_ -> dialog.close());
    }
}
