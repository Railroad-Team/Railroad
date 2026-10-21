package dev.railroadide.railroad.command;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.RailroadProcessLauncher;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceMode;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.projectexplorer.FileCreateType;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.ide.ui.IDEWorkspaceActions;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.ide.ui.editor.EditorTabManager;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.vcs.git.GitRepositoryState;
import dev.railroadide.railroad.window.AlertType;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroad.window.WindowManager;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Built-in file, editor-tab, explorer, and workspace command definitions.
 */
public final class Commands {
    /**
     * Opens the file-creation dialog in the selected directory.
     */
    public static final Command<Void> NEW_FILE = registerBasicCommand(
        "railroad:new_file",
        "railroad.menu.file.new_file",
        context -> context.project() != null,
        context -> CreateFileDialog.open(
            Railroad.WINDOW_MANAGER.getPrimaryStage(),
            selectedDirectory(context.project()),
            FileCreateType.FILE),
        List.of(new KeybindData(KeyCode.N, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Opens the file chooser and loads the selected file.
     */
    public static final Command<Void> OPEN_FILE = registerBasicCommand(
        "railroad:open_file",
        "railroad.menu.file.open_file",
        context -> context.project() != null,
        context -> openFile(context.project()),
        List.of(new KeybindData(KeyCode.O, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Chooses a project and opens the window-selection dialog.
     */
    public static final Command<Void> OPEN_PROJECT = registerBasicCommand(
        "railroad:open_project",
        "railroad.menu.file.open_project",
        context -> context.project() != null,
        context -> chooseProject(context.project()),
        List.of(new KeybindData(
            KeyCode.O,
            new KeyCombination.Modifier[]{
                KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN,
                KeyCombination.ALT_DOWN
            })));

    /**
     * Opens the window-selection dialog for the supplied project.
     */
    public static final Command<Project> OPEN_RECENT_PROJECT = CommandRegistry.register(new Command<>(
        "railroad:open_recent_project",
        "railroad.menu.file.recent_projects",
        context -> context.argument() != null,
        context -> showOpenProjectDialog(context.argument()),
        List.of(),
        Project.class));

    /**
     * Requests application closure through the unsaved-changes guard.
     */
    public static final Command<Void> EXIT = registerBasicCommand(
        "railroad:exit",
        "railroad.menu.file.exit",
        _ -> true,
        _ -> exitApplication(),
        List.of(new KeybindData(KeyCode.Q, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Invokes the reset current layout action on the supplied target.
     */
    public static final Command<IDEWorkspaceActions> RESET_CURRENT_LAYOUT = registerWorkspaceCommand(
        "railroad:reset_current_layout",
        "railroad.menu.view.reset_current_layout",
        IDEWorkspaceActions::resetCurrentLayout);

    /**
     * Invokes the reset all layouts action on the supplied target.
     */
    public static final Command<IDEWorkspaceActions> RESET_ALL_LAYOUTS = registerWorkspaceCommand(
        "railroad:reset_all_layouts",
        "railroad.menu.view.reset_all_layouts",
        IDEWorkspaceActions::resetAllLayouts);

    /**
     * Retries repository detection after a failure.
     */
    public static final Command<Void> RETRY_GIT_DETECTION = registerBasicCommand(
        "railroad:retry_git_detection",
        "railroad.ide.view_mode.git_detection_failed",
        context -> context.project() != null &&
            context.project().getGitManager().getRepositoryState() == GitRepositoryState.FAILED,
        context -> context.project().getGitManager().detectRepository(),
        List.of());

    /**
     * Invokes the open settings action on the supplied target.
     */
    public static final Command<Void> OPEN_SETTINGS = registerBasicCommand(
        "railroad:open_settings",
        "railroad.menu.tools.settings",
        _ -> true,
        _ -> SettingsPane.openSettingsWindow(),
        List.of(new KeybindData(KeyCode.COMMA, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Invokes the open plugins action on the supplied target.
     */
    public static final Command<Void> OPEN_PLUGINS = registerBasicCommand(
        "railroad:open_plugins",
        "railroad.menu.tools.plugins",
        _ -> true,
        _ -> SettingsPane.openPluginsWindow(),
        List.of());

    private static final Map<IDEDockItem, Command<IDEWorkspaceActions>> TOGGLE_DOCK_ITEM_COMMANDS = createDockItemCommands();
    private static final Map<IDEDockItem, Command<IDEWorkspaceActions>> DETACH_DOCK_ITEM_COMMANDS = createDetachDockItemCommands();
    private static final Map<IDEDockItem, Command<IDEWorkspaceActions>> RESET_DOCK_ITEM_COMMANDS = createResetDockItemCommands();

    /**
     * Invokes the open project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> OPEN_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:open_project_explorer_item",
        "railroad.settings.keybinds.open_project_explorer_item",
        ExplorerTarget::open,
        List.of(new KeybindData(KeyCode.ENTER, new KeyCombination.Modifier[0])));

    /**
     * Invokes the delete project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> DELETE_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:delete_project_explorer_item",
        "railroad.settings.keybinds.delete_project_explorer_item",
        ExplorerTarget::delete,
        List.of(new KeybindData(KeyCode.DELETE, new KeyCombination.Modifier[0])));

    /**
     * Invokes the cut project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> CUT_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:cut",
        "railroad.settings.keybinds.cut",
        ExplorerTarget::cut,
        List.of(new KeybindData(KeyCode.X, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Invokes the copy project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> COPY_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:copy",
        "railroad.settings.keybinds.copy",
        ExplorerTarget::copy,
        List.of(new KeybindData(KeyCode.C, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Invokes the paste project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> PASTE_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:paste",
        "railroad.settings.keybinds.paste",
        ExplorerTarget::paste,
        List.of(new KeybindData(KeyCode.V, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Invokes the create project explorer file action on the supplied target.
     */
    public static final Command<ExplorerTarget> CREATE_PROJECT_EXPLORER_FILE = registerProjectExplorerCommand(
        "railroad:create_file",
        "railroad.settings.keybinds.create_file",
        pane -> pane.create(FileCreateType.FILE),
        List.of(new KeybindData(KeyCode.N, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN})));

    /**
     * Invokes the create project explorer folder action on the supplied target.
     */
    public static final Command<ExplorerTarget> CREATE_PROJECT_EXPLORER_FOLDER = registerProjectExplorerCommand(
        "railroad:create_folder",
        "railroad.settings.keybinds.create_folder",
        pane -> pane.create(FileCreateType.FOLDER),
        List.of(new KeybindData(
            KeyCode.N,
            new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN})));

    /**
     * Invokes the rename project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> RENAME_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:rename_project_explorer_item",
        "railroad.settings.keybinds.rename_project_explorer_item",
        ExplorerTarget::rename,
        List.of(new KeybindData(KeyCode.R, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN})));

    /**
     * Invokes the reveal project explorer item action on the supplied target.
     */
    public static final Command<ExplorerTarget> REVEAL_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        "railroad:open_in_file_explorer",
        "railroad.settings.keybinds.open_in_file_explorer",
        ExplorerTarget::reveal,
        List.of(new KeybindData(KeyCode.O, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN})));

    /**
     * Invokes the open project explorer item in terminal action on the supplied target.
     */
    public static final Command<ExplorerTarget> OPEN_PROJECT_EXPLORER_ITEM_IN_TERMINAL = registerProjectExplorerCommand(
        "railroad:open_in_terminal",
        "railroad.settings.keybinds.open_in_terminal",
        ExplorerTarget::terminal,
        List.of(new KeybindData(KeyCode.T, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN})));

    /**
     * Saves the active editor and reports failures.
     */
    public static final Command<Void> SAVE = registerBasicCommand(
        "railroad:save",
        "railroad.menu.file.save",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab()
            .map(tab -> tab.view().activeEditor() != null)
            .orElse(false),
        _ -> showSaveFailures(Services.EDITOR_TAB_MANAGER.saveActive()),
        List.of(new KeybindData(
            KeyCode.S,
            new KeyCombination.Modifier[]{
                KeyCombination.SHORTCUT_DOWN
            })));

    /**
     * Saves the active editor to a user-selected path.
     */
    public static final Command<Void> SAVE_AS = registerBasicCommand(
        "railroad:save_as",
        "railroad.menu.file.save_as",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab()
            .map(tab -> tab.view().activeEditor() != null)
            .orElse(false),
        _ -> saveActiveAs(),
        List.of(new KeybindData(
            KeyCode.S,
            new KeyCombination.Modifier[]{
                KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN
            })));

    /**
     * Saves dirty editors and reports failures.
     */
    public static final Command<Void> SAVE_ALL = registerBasicCommand(
        "railroad:save_all",
        "railroad.menu.file.save_all",
        _ -> Services.EDITOR_TAB_MANAGER.hasUnsavedChanges(),
        _ -> showSaveFailures(Services.EDITOR_TAB_MANAGER.saveAll()),
        List.of(new KeybindData(
            KeyCode.S,
            new KeyCombination.Modifier[]{
                KeyCombination.SHORTCUT_DOWN,
                KeyCombination.ALT_DOWN
            })));

    /**
     * Invokes the navigate back action on the supplied target.
     */
    public static final Command<IDEWorkspaceActions> NAVIGATE_BACK = CommandRegistry.register(new Command<>(
        "railroad:navigate_back",
        "railroad.menu.view.navigate_back",
        context -> context.argument() != null &&
            context.argument().canNavigateBack(),
        context -> context.argument().navigateBack(),
        List.of(
            new KeybindData(
                KeyCode.LEFT,
                new KeyCombination.Modifier[]{
                    KeyCombination.ALT_DOWN
                }),
            new KeybindData(
                MouseButton.BACK,
                new KeyCombination.Modifier[0])),
        IDEWorkspaceActions.class));

    /**
     * Invokes the navigate forward action on the supplied target.
     */
    public static final Command<IDEWorkspaceActions> NAVIGATE_FORWARD = CommandRegistry.register(new Command<>(
        "railroad:navigate_forward",
        "railroad.menu.view.navigate_forward",
        context -> context.argument() != null &&
            context.argument().canNavigateForward(),
        context -> context.argument().navigateForward(),
        List.of(
            new KeybindData(
                KeyCode.RIGHT,
                new KeyCombination.Modifier[]{
                    KeyCombination.ALT_DOWN
                }),
            new KeybindData(
                MouseButton.FORWARD,
                new KeyCombination.Modifier[0])),
        IDEWorkspaceActions.class));

    /**
     * Invokes the close editor tab action on the supplied target.
     */
    public static final Command<EditorTab> CLOSE_EDITOR_TAB = CommandRegistry.register(new Command<>(
        "railroad:close_editor_tab",
        "editor.tab.contextmenu.close",
        context -> context.argument() != null,
        context -> Services.EDITOR_TAB_MANAGER.close(context.argument()),
        List.of(
            new KeybindData(
                KeyCode.W,
                new KeyCombination.Modifier[]{
                    KeyCombination.SHORTCUT_DOWN
                }),
            new KeybindData(
                MouseButton.MIDDLE,
                new KeyCombination.Modifier[0])),
        EditorTab.class));

    /**
     * Invokes the reopen closed editor tab action on the supplied target.
     */
    public static final Command<Void> REOPEN_CLOSED_EDITOR_TAB = registerBasicCommand(
        "railroad:reopen_closed_editor_tab",
        "editor.tab.contextmenu.reopen_closed_tab",
        _ -> Services.EDITOR_TAB_MANAGER.hasRecentlyClosedTabs(),
        _ -> Services.EDITOR_TAB_MANAGER.reopenLastClosed(),
        List.of(new KeybindData(
            KeyCode.T,
            new KeyCombination.Modifier[]{
                KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN
            })));

    /**
     * Invokes the toggle pin editor tab action on the supplied target.
     */
    public static final Command<EditorTab> TOGGLE_PIN_EDITOR_TAB = registerEditorTabCommand(
        "railroad:toggle_pin_editor_tab",
        "railroad.settings.keybinds.toggle_pin_editor_tab",
        context -> context.argument() != null,
        context -> Services.EDITOR_TAB_MANAGER.togglePin(context.argument()),
        List.of(new KeybindData(
            KeyCode.P,
            new KeyCombination.Modifier[]{
                KeyCombination.SHORTCUT_DOWN,
                KeyCombination.ALT_DOWN
            })));

    /**
     * Invokes the close other editor tabs action on the supplied target.
     */
    public static final Command<EditorTab> CLOSE_OTHER_EDITOR_TABS = registerEditorTabCommand(
        "railroad:close_other_editor_tabs",
        "editor.tab.contextmenu.close_others",
        context -> context.argument() != null &&
            Services.EDITOR_TAB_MANAGER.hasOtherClosableTabs(context.argument()),
        context -> Services.EDITOR_TAB_MANAGER.closeOthers(context.argument()),
        List.of(
            new KeybindData(
                KeyCode.W,
                new KeyCombination.Modifier[]{
                    KeyCombination.SHORTCUT_DOWN,
                    KeyCombination.ALT_DOWN
                }),
            new KeybindData(
                MouseButton.MIDDLE,
                new KeyCombination.Modifier[]{
                    KeyCombination.ALT_DOWN
                })));

    /**
     * Invokes the close editor tabs to left action on the supplied target.
     */
    public static final Command<EditorTab> CLOSE_EDITOR_TABS_TO_LEFT = registerEditorTabCommand(
        "railroad:close_editor_tabs_to_left",
        "editor.tab.contextmenu.close_to_left",
        context -> context.argument() != null &&
            Services.EDITOR_TAB_MANAGER.hasTabsToLeft(context.argument()),
        context -> Services.EDITOR_TAB_MANAGER.closeToLeft(context.argument()),
        List.of());

    /**
     * Invokes the close editor tabs to right action on the supplied target.
     */
    public static final Command<EditorTab> CLOSE_EDITOR_TABS_TO_RIGHT = registerEditorTabCommand(
        "railroad:close_editor_tabs_to_right",
        "editor.tab.contextmenu.close_to_right",
        context -> context.argument() != null &&
            Services.EDITOR_TAB_MANAGER.hasTabsToRight(context.argument()),
        context -> Services.EDITOR_TAB_MANAGER.closeToRight(context.argument()),
        List.of());

    /**
     * Invokes the close all editor tabs action on the supplied target.
     */
    public static final Command<Void> CLOSE_ALL_EDITOR_TABS = registerBasicCommand(
        "railroad:close_all_editor_tabs",
        "editor.tab.contextmenu.close_all",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.closeAll(),
        List.of());

    /**
     * Invokes the close all unpinned editor tabs action on the supplied target.
     */
    public static final Command<Void> CLOSE_ALL_UNPINNED_EDITOR_TABS = registerBasicCommand(
        "railroad:close_all_unpinned_editor_tabs",
        "editor.tab.contextmenu.close_all_unpinned",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.closeAllUnpinned(),
        List.of());

    /**
     * Invokes the close all unmodified editor tabs action on the supplied target.
     */
    public static final Command<Void> CLOSE_ALL_UNMODIFIED_EDITOR_TABS = registerBasicCommand(
        "railroad:close_all_unmodified_editor_tabs",
        "editor.tab.contextmenu.close_all_unmodified",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.closeAllUnmodified(),
        List.of());

    /**
     * Invokes the close all saved editor tabs action on the supplied target.
     */
    public static final Command<Void> CLOSE_ALL_SAVED_EDITOR_TABS = registerBasicCommand(
        "railroad:close_all_saved_editor_tabs",
        "editor.tab.contextmenu.close_all_saved",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.closeAllSaved(),
        List.of());

    /**
     * Invokes the copy editor tab absolute path action on the supplied target.
     */
    public static final Command<EditorTab> COPY_EDITOR_TAB_ABSOLUTE_PATH = registerEditorTabCommand(
        "railroad:copy_editor_tab_absolute_path",
        "editor.tab.contextmenu.copy_absolute_path",
        context -> context.argument() != null,
        context -> copyToClipboard(context.argument().path().toAbsolutePath().normalize().toString()),
        List.of());

    /**
     * Invokes the copy editor tab project relative path action on the supplied target.
     */
    public static final Command<EditorTab> COPY_EDITOR_TAB_PROJECT_RELATIVE_PATH = registerEditorTabCommand(
        "railroad:copy_editor_tab_project_relative_path",
        "editor.tab.contextmenu.copy_project_relative_path",
        context -> projectRelativePath(context) != null,
        context -> copyToClipboard(projectRelativePath(context).toString()),
        List.of());

    /**
     * Invokes the reveal editor tab in file explorer action on the supplied target.
     */
    public static final Command<EditorTab> REVEAL_EDITOR_TAB_IN_FILE_EXPLORER = registerEditorTabCommand(
        "railroad:reveal_editor_tab_in_file_explorer",
        "editor.tab.contextmenu.reveal_in_file_explorer",
        context -> context.argument() != null && Files.exists(context.argument().path()),
        context -> Services.EDITOR_TAB_MANAGER.revealInFileExplorer(context.argument()),
        List.of());

    /**
     * Invokes the reveal editor tab in project explorer action on the supplied target.
     */
    public static final Command<EditorTab> REVEAL_EDITOR_TAB_IN_PROJECT_EXPLORER = registerEditorTabCommand(
        "railroad:reveal_editor_tab_in_project_explorer",
        "editor.tab.contextmenu.reveal_in_project_explorer",
        context -> context.argument() != null && Files.exists(context.argument().path()),
        context -> Services.EDITOR_TAB_MANAGER.revealInProjectExplorer(context.argument()),
        List.of());

    /**
     * Invokes the open editor tab in terminal action on the supplied target.
     */
    public static final Command<EditorTab> OPEN_EDITOR_TAB_IN_TERMINAL = registerEditorTabCommand(
        "railroad:open_editor_tab_in_terminal",
        "editor.tab.contextmenu.open_in_terminal",
        context -> context.argument() != null &&
            Files.exists(context.argument().path()) &&
            context.argument().path().getParent() != null,
        context -> Services.EDITOR_TAB_MANAGER.openInTerminal(context.argument()),
        List.of());

    /**
     * Invokes the move editor tab to previous group action on the supplied target.
     */
    public static final Command<EditorTab> MOVE_EDITOR_TAB_TO_PREVIOUS_GROUP = registerEditorTabCommand(
        "railroad:move_editor_tab_to_previous_group",
        "editor.tab.contextmenu.move_to_previous_group",
        context -> context.argument() != null &&
            Services.EDITOR_TAB_MANAGER.hasPreviousEditorGroup(context.argument()),
        context -> Services.EDITOR_TAB_MANAGER.moveToPreviousGroup(context.argument()),
        List.of());

    /**
     * Invokes the move editor tab to next group action on the supplied target.
     */
    public static final Command<EditorTab> MOVE_EDITOR_TAB_TO_NEXT_GROUP = registerEditorTabCommand(
        "railroad:move_editor_tab_to_next_group",
        "editor.tab.contextmenu.move_to_next_group",
        context -> context.argument() != null &&
            Services.EDITOR_TAB_MANAGER.hasNextEditorGroup(context.argument()),
        context -> Services.EDITOR_TAB_MANAGER.moveToNextGroup(context.argument()),
        List.of());

    /**
     * Invokes the split editor tab right action on the supplied target.
     */
    public static final Command<EditorTab> SPLIT_EDITOR_TAB_RIGHT = registerEditorTabCommand(
        "railroad:split_editor_tab_right",
        "editor.tab.contextmenu.split_right",
        context -> context.argument() != null,
        context -> Services.EDITOR_TAB_MANAGER.splitRight(context.argument()),
        List.of());

    /**
     * Invokes the split editor tab down action on the supplied target.
     */
    public static final Command<EditorTab> SPLIT_EDITOR_TAB_DOWN = registerEditorTabCommand(
        "railroad:split_editor_tab_down",
        "editor.tab.contextmenu.split_down",
        context -> context.argument() != null,
        context -> Services.EDITOR_TAB_MANAGER.splitDown(context.argument()),
        List.of());

    /**
     * Invokes the open editor tab in new window action on the supplied target.
     */
    public static final Command<EditorTab> OPEN_EDITOR_TAB_IN_NEW_WINDOW = registerEditorTabCommand(
        "railroad:open_editor_tab_in_new_window",
        "editor.tab.contextmenu.open_in_new_window",
        context -> context.argument() != null,
        context -> Services.EDITOR_TAB_MANAGER.openInNewWindow(context.argument()),
        List.of());

    /**
     * Toggles the application's fullscreen state.
     */
    public static final Command<Void> FULLSCREEN = registerBasicCommand(
        "railroad:fullscreen",
        "railroad.menu.view.full_screen",
        _ -> true,
        _ -> WindowManager.toggleFullScreen(),
        List.of(new KeybindData(KeyCode.F11, new KeyCombination.Modifier[0])));

    /**
     * Invokes the view mode code action on the supplied target.
     */
    public static final Command<Void> VIEW_MODE_CODE = registerViewModeCommand(
        "railroad:view_mode_code",
        WorkspaceModes.CODE,
        KeyCode.DIGIT1);

    /**
     * Invokes the view mode git action on the supplied target.
     */
    public static final Command<Void> VIEW_MODE_GIT = registerViewModeCommand(
        "railroad:view_mode_git",
        WorkspaceModes.GIT,
        KeyCode.DIGIT2);

    /**
     * Selects the supplied zero-based tab index; a negative argument selects the last tab.
     */
    public static final Command<Integer> SELECT_EDITOR_TAB_BY_NUMBER = CommandRegistry.register(new Command<>(
        "railroad:select_editor_tab_by_number",
        "railroad.settings.keybinds.select_editor_tab_by_number",
        context -> context.argument() != null &&
            context.argument() != Integer.MIN_VALUE &&
            Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        context -> {
            if (context.argument() < 0) {
                Services.EDITOR_TAB_MANAGER.selectLastTab();
            } else {
                Services.EDITOR_TAB_MANAGER.selectTab(context.argument());
            }
        },
        List.of(
            new KeybindData(KeyCode.DIGIT1, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT2, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT3, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT4, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT5, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT6, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT7, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT8, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}),
            new KeybindData(KeyCode.DIGIT9, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})),
        Integer.class));

    /**
     * Invokes the select next editor tab action on the supplied target.
     */
    public static final Command<Void> SELECT_NEXT_EDITOR_TAB = registerBasicCommand(
        "railroad:select_next_editor_tab",
        "railroad.settings.keybinds.select_next_editor_tab",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.selectNextTab(),
        List.of(new KeybindData(KeyCode.TAB, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})));

    /**
     * Invokes the select previous editor tab action on the supplied target.
     */
    public static final Command<Void> SELECT_PREVIOUS_EDITOR_TAB = registerBasicCommand(
        "railroad:select_previous_editor_tab",
        "railroad.settings.keybinds.select_previous_editor_tab",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.selectPreviousTab(),
        List.of(new KeybindData(
            KeyCode.TAB,
            new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN})));

    /**
     * Invokes the move active editor tab left action on the supplied target.
     */
    public static final Command<Void> MOVE_ACTIVE_EDITOR_TAB_LEFT = registerBasicCommand(
        "railroad:move_editor_tab_left",
        "railroad.settings.keybinds.move_editor_tab_left",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.moveActiveTabLeft(),
        List.of(new KeybindData(
            KeyCode.PAGE_UP,
            new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN})));

    /**
     * Invokes the move active editor tab right action on the supplied target.
     */
    public static final Command<Void> MOVE_ACTIVE_EDITOR_TAB_RIGHT = registerBasicCommand(
        "railroad:move_editor_tab_right",
        "railroad.settings.keybinds.move_editor_tab_right",
        _ -> Services.EDITOR_TAB_MANAGER.activeTab().isPresent(),
        _ -> Services.EDITOR_TAB_MANAGER.moveActiveTabRight(),
        List.of(new KeybindData(
            KeyCode.PAGE_DOWN,
            new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN})));

    /**
     * Invokes the create java class action on the supplied target.
     */
    public static final Command<ExplorerTarget> CREATE_JAVA_CLASS = registerProjectExplorerCommand(
        "railroad:create_java_class", "railroad.command.create_java_class", t -> t.create(FileCreateType.JAVA_CLASS),
        List.of());
    /**
     * Invokes the create json action on the supplied target.
     */
    public static final Command<ExplorerTarget> CREATE_JSON = registerProjectExplorerCommand(
        "railroad:create_json", "railroad.command.create_json", t -> t.create(FileCreateType.JSON), List.of());
    /**
     * Invokes the create text action on the supplied target.
     */
    public static final Command<ExplorerTarget> CREATE_TEXT = registerProjectExplorerCommand(
        "railroad:create_text", "railroad.command.create_text", t -> t.create(FileCreateType.TXT), List.of());
    /**
     * Invokes the expand explorer action on the supplied target.
     */
    public static final Command<ExplorerTarget> EXPAND_EXPLORER = registerProjectExplorerCommand(
        "railroad:expand_explorer", "railroad.command.expand_explorer", ExplorerTarget::expand, List.of());
    /**
     * Invokes the collapse explorer action on the supplied target.
     */
    public static final Command<ExplorerTarget> COLLAPSE_EXPLORER = registerProjectExplorerCommand(
        "railroad:collapse_explorer", "railroad.command.collapse_explorer", ExplorerTarget::collapse, List.of());
    /**
     * Invokes the refresh explorer action on the supplied target.
     */
    public static final Command<ProjectExplorerPane> REFRESH_EXPLORER = CommandRegistry.register(new Command<>(
        "railroad:refresh_explorer", "railroad.command.refresh_explorer", c -> c.argument() != null,
        c -> c.argument().refreshProjectExplorer(), List.of(), ProjectExplorerPane.class));

    private static Command<Void> registerBasicCommand(
        String id,
        String displayNameKey,
        Predicate<CommandContext<Void>> enabled,
        Consumer<CommandContext<Void>> handler,
        List<KeybindData> defaultShortcuts
    ) {
        return CommandRegistry.register(new Command<>(
            id,
            displayNameKey,
            enabled,
            handler,
            List.copyOf(defaultShortcuts),
            Void.class));
    }

    private static Command<EditorTab> registerEditorTabCommand(
        String id,
        String displayNameKey,
        Predicate<CommandContext<EditorTab>> enabled,
        Consumer<CommandContext<EditorTab>> handler,
        List<KeybindData> defaultShortcuts
    ) {
        return CommandRegistry.register(new Command<>(
            id,
            displayNameKey,
            enabled,
            handler,
            List.copyOf(defaultShortcuts),
            EditorTab.class));
    }

    private static Command<ExplorerTarget> registerProjectExplorerCommand(
        String id,
        String displayNameKey,
        Consumer<ExplorerTarget> handler,
        List<KeybindData> defaultShortcuts
    ) {
        return CommandRegistry.register(new Command<>(
            id,
            displayNameKey,
            context -> context.argument() != null && context.argument().valid() &&
                (!id.equals("railroad:paste") || Clipboard.getSystemClipboard().hasFiles()) &&
                (!(id.equals("railroad:cut") || id.equals("railroad:copy")) || !context.argument().isCut()),
            context -> handler.accept(context.argument()),
            List.copyOf(defaultShortcuts),
            ExplorerTarget.class));
    }

    private static Command<IDEWorkspaceActions> registerWorkspaceCommand(
        String id,
        String displayNameKey,
        Consumer<IDEWorkspaceActions> handler
    ) {
        return CommandRegistry.register(new Command<>(
            id,
            displayNameKey,
            context -> context.argument() != null,
            context -> handler.accept(context.argument()),
            List.of(),
            IDEWorkspaceActions.class));
    }

    private static Map<IDEDockItem, Command<IDEWorkspaceActions>> createDockItemCommands() {
        Map<IDEDockItem, Command<IDEWorkspaceActions>> commands = new EnumMap<>(IDEDockItem.class);
        for (IDEDockItem dockItem : IDEDockItem.values()) {
            List<KeybindData> shortcuts = dockItem == IDEDockItem.TERMINAL
                ? List.of(new KeybindData(
                    KeyCode.BACK_QUOTE,
                    new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}))
                : List.of();
            commands.put(dockItem, CommandRegistry.register(new Command<>(
                "railroad:toggle_" + dockItem.id().replace("dock-item:", "").replace('-', '_'),
                dockItem.localizationKey(),
                context -> context.argument() != null && context.argument().isDockItemAvailable(dockItem),
                context -> context.argument().toggleDockItem(dockItem),
                shortcuts,
                IDEWorkspaceActions.class)));
        }
        return Map.copyOf(commands);
    }

    /**
     * Returns the shared visibility command for a dock item.
     *
     * @param dockItem dock descriptor identifying the tool
     * @return registered toggle command
     */
    public static Command<IDEWorkspaceActions> toggleDockItem(IDEDockItem dockItem) {
        return TOGGLE_DOCK_ITEM_COMMANDS.get(dockItem);
    }

    private static Map<IDEDockItem, Command<IDEWorkspaceActions>> createDetachDockItemCommands() {
        Map<IDEDockItem, Command<IDEWorkspaceActions>> commands = new EnumMap<>(IDEDockItem.class);
        for (IDEDockItem dockItem : IDEDockItem.values()) {
            commands.put(dockItem, CommandRegistry.register(new Command<>(
                "railroad:detach_" + dockItem.id().replace("dock-item:", "").replace('-', '_'),
                "tool.tab.contextmenu.detach",
                context -> context.argument() != null &&
                    context.argument().isDockItemAvailable(dockItem) &&
                    !context.argument().isDockItemDetached(dockItem),
                context -> context.argument().detachDockItem(dockItem),
                List.of(),
                IDEWorkspaceActions.class)));
        }
        return Map.copyOf(commands);
    }

    private static Map<IDEDockItem, Command<IDEWorkspaceActions>> createResetDockItemCommands() {
        Map<IDEDockItem, Command<IDEWorkspaceActions>> commands = new EnumMap<>(IDEDockItem.class);
        for (IDEDockItem dockItem : IDEDockItem.values()) {
            commands.put(dockItem, CommandRegistry.register(new Command<>(
                "railroad:reset_" + dockItem.id().replace("dock-item:", "").replace('-', '_') + "_position",
                "tool.tab.contextmenu.reset_position",
                context -> context.argument() != null && context.argument().isDockItemDetached(dockItem),
                context -> context.argument().resetDockItemPosition(dockItem),
                List.of(),
                IDEWorkspaceActions.class)));
        }
        return Map.copyOf(commands);
    }

    /**
     * Returns the command for moving a dock item into a window.
     *
     * @param dockItem dock descriptor identifying the tool
     * @return registered detach command
     */
    public static Command<IDEWorkspaceActions> detachDockItem(IDEDockItem dockItem) {
        return DETACH_DOCK_ITEM_COMMANDS.get(dockItem);
    }

    /**
     * Returns the command for restoring a dock item's position.
     *
     * @param dockItem dock descriptor identifying the tool
     * @return registered reset command
     */
    public static Command<IDEWorkspaceActions> resetDockItemPosition(IDEDockItem dockItem) {
        return RESET_DOCK_ITEM_COMMANDS.get(dockItem);
    }

    private static Command<Void> registerViewModeCommand(
        String id,
        WorkspaceMode mode,
        KeyCode keyCode
    ) {
        return registerBasicCommand(
            id,
            mode.getLocalizationKey(),
            context -> mode.isAvailable(context.project()),
            _ -> Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
                .ifPresent(idePane -> idePane.requestViewMode(mode)),
            List.of(new KeybindData(
                keyCode,
                new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN})));
    }

    /**
     * Returns the command for requesting the supplied workspace mode.
     *
     * @param mode workspace mode to request
     * @return registered workspace-mode command, or null for an unregistered mode
     */
    public static Command<Void> viewMode(WorkspaceMode mode) {
        if (mode == WorkspaceModes.CODE)
            return VIEW_MODE_CODE;
        if (mode == WorkspaceModes.GIT)
            return VIEW_MODE_GIT;
        return null;
    }

    /**
     * Initializes the registered commands through static init
     */
    public static void initialize() {
        RunCommands.initialize();
        EditCommands.initialize();
        GitCommands.initialize();
        GradleCommands.initialize();
        MarkdownCommands.initialize();
        ApplicationCommands.initialize();
    }

    private static Path selectedDirectory(Project project) {
        return Services.UI_MANAGER.lookup(UIIds.IDE.PROJECT_EXPLORER)
            .map(ProjectExplorerPane::getSelectedDirectory)
            .orElseGet(project::getPath);
    }

    private static void openFile(Project project) {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(L18n.localize("railroad.menu.file.open_file"));
        fileChooser.setInitialDirectory(selectedDirectory(project).toFile());
        File file = fileChooser.showOpenDialog(Railroad.WINDOW_MANAGER.getPrimaryStage());
        if (file != null) {
            Services.EDITOR_TAB_MANAGER.open(file.toPath());
        }
    }

    private static void chooseProject(Project project) {
        var directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(L18n.localize("railroad.menu.file.open_project"));
        directoryChooser.setInitialDirectory(selectedDirectory(project).toFile());
        File directory = directoryChooser.showDialog(Railroad.WINDOW_MANAGER.getPrimaryStage());
        if (directory == null)
            return;

        Project createdProject = Railroad.PROJECT_MANAGER.newProject(new RailroadProject(directory.toPath()));
        showOpenProjectDialog(createdProject);
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

    private static void exitApplication() {
        Stage stage = Railroad.WINDOW_MANAGER.getPrimaryStage();
        var closeRequest = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        Event.fireEvent(stage, closeRequest);
        if (!closeRequest.isConsumed()) {
            Platform.exit();
        }
    }

    private static void saveActiveAs() {
        var activeTab = Services.EDITOR_TAB_MANAGER.activeTab().orElse(null);
        if (activeTab == null || activeTab.view().activeEditor() == null)
            return;

        var fileChooser = new FileChooser();
        fileChooser.setTitle(L18n.localize("railroad.menu.file.save_as"));
        Path parent = activeTab.path().getParent();
        if (parent != null && parent.toFile().isDirectory()) {
            fileChooser.setInitialDirectory(parent.toFile());
        }
        fileChooser.setInitialFileName(activeTab.path().getFileName().toString());
        File targetFile = fileChooser.showSaveDialog(Railroad.WINDOW_MANAGER.getPrimaryStage());
        if (targetFile == null)
            return;

        if (!Services.EDITOR_TAB_MANAGER.saveAsActive(targetFile.toPath())) {
            showSaveFailures(new EditorTabManager.SaveResult(List.of(activeTab)));
        }
    }

    private static void showSaveFailures(EditorTabManager.SaveResult result) {
        if (result.successful())
            return;

        String paths = result.failedTabs().stream()
            .map(tab -> tab.path().toString())
            .collect(Collectors.joining(System.lineSeparator()));
        WindowBuilder.createAlert(
            AlertType.ERROR,
            "railroad.generic.error",
            "railroad.ide.save_failed.title",
            L18n.localize("railroad.ide.save_failed.content", paths),
            alert -> alert.translateContent(false),
            null).build();
    }

    private static Path projectRelativePath(CommandContext<EditorTab> context) {
        if (context.project() == null || context.argument() == null)
            return null;

        Path projectPath = context.project().getPath().toAbsolutePath().normalize();
        Path documentPath = context.argument().path().toAbsolutePath().normalize();
        return documentPath.startsWith(projectPath) ? projectPath.relativize(documentPath) : null;
    }

    private static void copyToClipboard(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
