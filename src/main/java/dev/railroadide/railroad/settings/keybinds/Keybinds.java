package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.*;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.ide.ui.IDEWorkspaceActions;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.scene.input.KeyEvent;

import java.util.Map;

/**
 * Built-in keybind definitions for project navigation, editing, and workspace controls.
 */
public class Keybinds {
    private static final KeybindCategory GENERAL = new KeybindCategory("railroad:general",
        "railroad.settings.keybinds.category.general");
    private static final KeybindCategory VIEW_MODES = new KeybindCategory("railroad:view_modes",
        "railroad.settings.keybinds.category.view_modes");
    private static final KeybindContexts.KeybindContext PROJECT_EXPLORER = KeybindContexts
        .of("railroad:project_explorer");
    private static final KeybindContexts.KeybindContext IDE = KeybindContexts.of("railroad:ide");
    /**
     * Context used while interacting with editor tabs.
     */
    public static final KeybindContexts.KeybindContext EDITOR_TABS = KeybindContexts.of("railroad:editor_tabs");

    /**
     * Opens the selected project-explorer item.
     */
    public static final Keybind OPEN_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        Commands.OPEN_PROJECT_EXPLORER_ITEM);

    /**
     * Deletes the selected project-explorer item.
     */
    public static final Keybind DELETE = registerProjectExplorerCommand(Commands.DELETE_PROJECT_EXPLORER_ITEM);

    /**
     * Cuts the selected project-explorer item.
     */
    public static final Keybind CUT = registerProjectExplorerCommand(Commands.CUT_PROJECT_EXPLORER_ITEM);

    /**
     * Copies the selected project-explorer item.
     */
    public static final Keybind COPY = registerProjectExplorerCommand(Commands.COPY_PROJECT_EXPLORER_ITEM);

    /**
     * Pastes into the selected project-explorer item.
     */
    public static final Keybind PASTE = registerProjectExplorerCommand(Commands.PASTE_PROJECT_EXPLORER_ITEM);

    /**
     * Creates a file beneath the selected project-explorer item.
     */
    public static final Keybind CREATE_FILE = registerProjectExplorerCommand(Commands.CREATE_PROJECT_EXPLORER_FILE);

    /**
     * Creates a folder beneath the selected project-explorer item.
     */
    public static final Keybind CREATE_FOLDER = registerProjectExplorerCommand(Commands.CREATE_PROJECT_EXPLORER_FOLDER);

    /**
     * Renames the selected project-explorer item.
     */
    public static final Keybind RENAME_PROJECT_EXPLORER_ITEM = registerProjectExplorerCommand(
        Commands.RENAME_PROJECT_EXPLORER_ITEM);

    /**
     * Opens the selected item in the operating system's file explorer.
     */
    public static final Keybind OPEN_IN_FILE_EXPLORER = registerProjectExplorerCommand(
        Commands.REVEAL_PROJECT_EXPLORER_ITEM);

    /**
     * Opens a terminal for the selected project-explorer item.
     */
    public static final Keybind OPEN_IN_TERMINAL = registerProjectExplorerCommand(
        Commands.OPEN_PROJECT_EXPLORER_ITEM_IN_TERMINAL);

    /**
     * Configurable shortcut for the run command.
     */
    public static final Keybind RUN = registerIDECommand(RunCommands.RUN);
    /**
     * Configurable shortcut for the debug command.
     */
    public static final Keybind DEBUG = registerIDECommand(RunCommands.DEBUG);
    /**
     * Configurable shortcut for the stop command.
     */
    public static final Keybind STOP = registerIDECommand(RunCommands.STOP);
    /**
     * Configurable shortcut for the undo command.
     */
    public static final Keybind UNDO = registerIDECommand(EditCommands.UNDO);
    /**
     * Configurable shortcut for the redo command.
     */
    public static final Keybind REDO = registerIDECommand(EditCommands.REDO);
    /**
     * Configurable shortcut for the edit cut command.
     */
    public static final Keybind EDIT_CUT = registerIDECommand(EditCommands.CUT);
    /**
     * Configurable shortcut for the edit copy command.
     */
    public static final Keybind EDIT_COPY = registerIDECommand(EditCommands.COPY);
    /**
     * Configurable shortcut for the edit paste command.
     */
    public static final Keybind EDIT_PASTE = registerIDECommand(EditCommands.PASTE);

    /**
     * Configurable shortcut for the new file command.
     */
    public static final Keybind NEW_FILE = registerIDECommand(Commands.NEW_FILE);
    /**
     * Configurable shortcut for the open file command.
     */
    public static final Keybind OPEN_FILE = registerIDECommand(Commands.OPEN_FILE);
    /**
     * Configurable shortcut for the open project command.
     */
    public static final Keybind OPEN_PROJECT = registerIDECommand(Commands.OPEN_PROJECT);
    /**
     * Configurable shortcut for the exit command.
     */
    public static final Keybind EXIT = registerIDECommand(Commands.EXIT);
    /**
     * Configurable shortcut for the open settings command.
     */
    public static final Keybind OPEN_SETTINGS = registerIDECommand(Commands.OPEN_SETTINGS);
    /**
     * Configurable shortcut for the toggle terminal command.
     */
    public static final Keybind TOGGLE_TERMINAL = registerWorkspaceCommand(
        Commands.toggleDockItem(IDEDockItem.TERMINAL));

    /**
     * Toggles the application window's fullscreen state.
     */
    public static final Keybind FULLSCREEN = KeybindHandler.registerCommand(
        Commands.FULLSCREEN,
        GENERAL,
        IDE,
        action -> CommandContext.forProject(
            Railroad.PROJECT_MANAGER.getOpenProject(),
            action.target()));

    /**
     * Switches to the code workspace view.
     */
    public static final Keybind VIEW_MODE_CODE = registerViewModeCommand(Commands.VIEW_MODE_CODE);

    /**
     * Switches to the Git workspace view.
     */
    public static final Keybind VIEW_MODE_GIT = registerViewModeCommand(Commands.VIEW_MODE_GIT);

    /**
     * Navigates backward in the IDE navigation history.
     */
    public static final Keybind NAVIGATE_BACK = KeybindHandler.registerCommand(
        Commands.NAVIGATE_BACK,
        GENERAL,
        IDE,
        action -> CommandContext.withArgument(
            Railroad.PROJECT_MANAGER.getOpenProject(),
            action.target(),
            Services.UI_MANAGER.lookupOrThrow(UIIds.IDE.IDE)));

    /**
     * Navigates forward in the IDE navigation history.
     */
    public static final Keybind NAVIGATE_FORWARD = KeybindHandler.registerCommand(
        Commands.NAVIGATE_FORWARD,
        GENERAL,
        IDE,
        action -> CommandContext.withArgument(
            Railroad.PROJECT_MANAGER.getOpenProject(),
            action.target(),
            Services.UI_MANAGER.lookupOrThrow(UIIds.IDE.IDE)));

    /**
     * Closes the active or targeted editor tab.
     */
    public static final Keybind CLOSE_EDITOR_TAB = registerEditorTabCommand(Commands.CLOSE_EDITOR_TAB);

    /**
     * Reopens the most recently closed editor tab.
     */
    public static final Keybind REOPEN_CLOSED_EDITOR_TAB = registerIDECommand(Commands.REOPEN_CLOSED_EDITOR_TAB);

    /**
     * Selects an editor tab by its number.
     */
    public static final Keybind SELECT_EDITOR_TAB_BY_NUMBER = KeybindHandler.registerCommand(
        Commands.SELECT_EDITOR_TAB_BY_NUMBER,
        GENERAL,
        IDE,
        action -> CommandContext.withArgument(
            Railroad.PROJECT_MANAGER.getOpenProject(),
            action.target(),
            editorTabIndex(action)));

    /**
     * Selects the next editor tab.
     */
    public static final Keybind SELECT_NEXT_EDITOR_TAB = registerIDECommand(Commands.SELECT_NEXT_EDITOR_TAB);

    /**
     * Selects the previous editor tab.
     */
    public static final Keybind SELECT_PREVIOUS_EDITOR_TAB = registerIDECommand(Commands.SELECT_PREVIOUS_EDITOR_TAB);

    /**
     * Moves the active editor tab to the left.
     */
    public static final Keybind MOVE_EDITOR_TAB_LEFT = registerIDECommand(Commands.MOVE_ACTIVE_EDITOR_TAB_LEFT);

    /**
     * Moves the active editor tab to the right.
     */
    public static final Keybind MOVE_EDITOR_TAB_RIGHT = registerIDECommand(Commands.MOVE_ACTIVE_EDITOR_TAB_RIGHT);

    /**
     * Toggles the pinned state of the active editor tab.
     */
    public static final Keybind TOGGLE_PIN_EDITOR_TAB = registerEditorTabCommand(Commands.TOGGLE_PIN_EDITOR_TAB);

    /**
     * Closes every editor tab except the active or targeted tab.
     */
    public static final Keybind CLOSE_OTHER_EDITOR_TABS = registerEditorTabCommand(Commands.CLOSE_OTHER_EDITOR_TABS);

    /**
     * Configurable shortcut for the save command.
     */
    public static final Keybind SAVE = registerIDECommand(Commands.SAVE);
    /**
     * Configurable shortcut for the save as command.
     */
    public static final Keybind SAVE_AS = registerIDECommand(Commands.SAVE_AS);
    /**
     * Configurable shortcut for the save all command.
     */
    public static final Keybind SAVE_ALL = registerIDECommand(Commands.SAVE_ALL);

    /**
     * Restores persisted keybinds that support user-configurable combinations.
     */
    public static void initialize() {
        NAVIGATE_BACK.resetKeys();
        NAVIGATE_FORWARD.resetKeys();
        CLOSE_EDITOR_TAB.resetKeys();
        REOPEN_CLOSED_EDITOR_TAB.resetKeys();
        SELECT_EDITOR_TAB_BY_NUMBER.resetKeys();
        SELECT_NEXT_EDITOR_TAB.resetKeys();
        SELECT_PREVIOUS_EDITOR_TAB.resetKeys();
        MOVE_EDITOR_TAB_LEFT.resetKeys();
        MOVE_EDITOR_TAB_RIGHT.resetKeys();
        TOGGLE_PIN_EDITOR_TAB.resetKeys();
        CLOSE_OTHER_EDITOR_TABS.resetKeys();
    }

    private static Keybind registerProjectExplorerCommand(Command<ExplorerTarget> command) {
        return KeybindHandler.registerCommand(
            command,
            GENERAL,
            PROJECT_EXPLORER,
            action -> CommandContext.withArgument(
                Railroad.PROJECT_MANAGER.getOpenProject(),
                action.target(),
                projectExplorer().commandTarget()));
    }

    private static Keybind registerViewModeCommand(Command<Void> command) {
        return KeybindHandler.registerCommand(
            command,
            VIEW_MODES,
            IDE,
            action -> CommandContext.forProject(
                Railroad.PROJECT_MANAGER.getOpenProject(),
                action.target()));
    }

    private static <T> Keybind registerIDECommand(Command<T> command) {
        return KeybindHandler.registerCommand(
            command,
            GENERAL,
            IDE,
            action -> CommandContext.forProject(
                Railroad.PROJECT_MANAGER.getOpenProject(),
                action.target()));
    }

    private static Keybind registerWorkspaceCommand(Command<IDEWorkspaceActions> command) {
        return KeybindHandler.registerCommand(
            command,
            GENERAL,
            IDE,
            action -> CommandContext.withArgument(
                Railroad.PROJECT_MANAGER.getOpenProject(),
                action.target(),
                Services.UI_MANAGER.lookupOrThrow(UIIds.IDE.IDE)));
    }

    private static Keybind registerEditorTabCommand(Command<EditorTab> command) {
        return KeybindHandler.registerCommand(
            command,
            GENERAL,
            Map.of(
                IDE,
                action -> CommandContext.withArgument(
                    Railroad.PROJECT_MANAGER.getOpenProject(),
                    action.target(),
                    Services.EDITOR_TAB_MANAGER.activeTab().orElse(null)),
                EDITOR_TABS,
                action -> CommandContext.withArgument(
                    Railroad.PROJECT_MANAGER.getOpenProject(),
                    action.target(),
                    Services.EDITOR_TAB_MANAGER.getTabAt(action.target()))));
    }

    private static ProjectExplorerPane projectExplorer() {
        return Services.UI_MANAGER.lookupOrThrow(UIIds.IDE.PROJECT_EXPLORER);
    }

    private static int editorTabIndex(KeybindActionContext action) {
        if (!(action.event() instanceof KeyEvent keyEvent))
            return Integer.MIN_VALUE;

        return switch (keyEvent.getCode()) {
            case DIGIT1 -> 0;
            case DIGIT2 -> 1;
            case DIGIT3 -> 2;
            case DIGIT4 -> 3;
            case DIGIT5 -> 4;
            case DIGIT6 -> 5;
            case DIGIT7 -> 6;
            case DIGIT8 -> 7;
            case DIGIT9 -> -1;
            default -> Integer.MIN_VALUE;
        };
    }
}
