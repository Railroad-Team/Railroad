package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceMode;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.projectexplorer.FileCreateType;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.window.WindowManager;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;

import java.util.function.Consumer;

public class Keybinds {
    private static final KeybindCategory GENERAL = new KeybindCategory("railroad:general",
        "railroad.settings.keybinds.category.general");
    private static final KeybindCategory VIEW_MODES = new KeybindCategory("railroad:view_modes",
        "railroad.settings.keybinds.category.view_modes");
    private static final KeybindContexts.KeybindContext PROJECT_EXPLORER = KeybindContexts
        .of("railroad:project_explorer");
    private static final KeybindContexts.KeybindContext IDE = KeybindContexts.of("railroad:ide");
    public static final KeybindContexts.KeybindContext EDITOR_TABS = KeybindContexts.of("railroad:editor_tabs");

    public static final Keybind OPEN_PROJECT_EXPLORER_ITEM = registerProjectExplorerKeybind(
        "railroad:open_project_explorer_item",
        KeyCode.ENTER,
        ProjectExplorerPane::openSelectedItem);

    public static final Keybind DELETE = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:delete_project_explorer_item")
        .category(GENERAL)
        .addDefaultKey(KeyCode.DELETE)
        .addAction(PROJECT_EXPLORER, _ -> projectExplorer().deleteSelectedItem())
        .build());

    public static final Keybind CUT = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:cut")
        .category(GENERAL)
        .addDefaultKey(KeyCode.X, KeyCombination.SHORTCUT_DOWN)
        .addAction(PROJECT_EXPLORER, _ -> projectExplorer().cutSelectedItem())
        .build());

    public static final Keybind COPY = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:copy")
        .category(GENERAL)
        .addDefaultKey(KeyCode.C, KeyCombination.SHORTCUT_DOWN)
        .addAction(PROJECT_EXPLORER, _ -> projectExplorer().copySelectedItem())
        .build());

    public static final Keybind PASTE = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:paste")
        .category(GENERAL)
        .addDefaultKey(KeyCode.V, KeyCombination.SHORTCUT_DOWN)
        .addAction(PROJECT_EXPLORER, _ -> projectExplorer().pasteIntoSelectedItem())
        .build());

    public static final Keybind CREATE_FILE = registerProjectExplorerKeybind(
        "railroad:create_file",
        KeyCode.N,
        pane -> pane.createFileInSelectedItem(FileCreateType.FILE),
        KeyCombination.CONTROL_DOWN);

    public static final Keybind CREATE_FOLDER = registerProjectExplorerKeybind(
        "railroad:create_folder",
        KeyCode.N,
        pane -> pane.createFileInSelectedItem(FileCreateType.FOLDER),
        KeyCombination.CONTROL_DOWN,
        KeyCombination.SHIFT_DOWN);

    public static final Keybind RENAME_PROJECT_EXPLORER_ITEM = registerProjectExplorerKeybind(
        "railroad:rename_project_explorer_item",
        KeyCode.R,
        ProjectExplorerPane::renameSelectedItem,
        KeyCombination.CONTROL_DOWN);

    public static final Keybind OPEN_IN_FILE_EXPLORER = registerProjectExplorerKeybind(
        "railroad:open_in_file_explorer",
        KeyCode.O,
        ProjectExplorerPane::openSelectedItemInExplorer,
        KeyCombination.CONTROL_DOWN);

    public static final Keybind OPEN_IN_TERMINAL = registerProjectExplorerKeybind(
        "railroad:open_in_terminal",
        KeyCode.T,
        ProjectExplorerPane::openSelectedItemInTerminal,
        KeyCombination.CONTROL_DOWN);

    public static final Keybind FULLSCREEN = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:fullscreen")
        .category(GENERAL)
        .addDefaultKey(KeyCode.F11)
        .addAction(IDE, _ -> WindowManager.toggleFullScreen())
        .build());

    public static final Keybind VIEW_MODE_CODE = registerViewModeKeybind(
        "railroad:view_mode_code",
        WorkspaceModes.CODE,
        KeyCode.DIGIT1);

    public static final Keybind VIEW_MODE_GIT = registerViewModeKeybind(
        "railroad:view_mode_git",
        WorkspaceModes.GIT,
        KeyCode.DIGIT2);

    public static final Keybind CLOSE_EDITOR_TAB = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:close_editor_tab")
        .category(GENERAL)
        .addDefaultMouseButton(MouseButton.MIDDLE)
        .addDefaultKey(KeyCode.W, KeyCombination.SHORTCUT_DOWN)
        .addValidContext(EDITOR_TABS)
        .addValidContext(IDE)
        .ignoreAllContext()
        .addAction(EDITOR_TABS, target -> {
            EditorTab editorTab = Services.EDITOR_TAB_MANAGER.getTabAt(target);
            if (editorTab != null) {
                Services.EDITOR_TAB_MANAGER.close(editorTab);
            }
        })
        .addAction(IDE,
            _ -> Services.EDITOR_TAB_MANAGER.activeTab().ifPresent(Services.EDITOR_TAB_MANAGER::close))
        .build());

    public static final Keybind CLOSE_OTHER_EDITOR_TABS = KeybindHandler.registerKeybind(Keybind.builder()
        .id("railroad:close_other_editor_tabs")
        .category(GENERAL)
        .addDefaultMouseButton(MouseButton.MIDDLE, KeyCombination.ALT_DOWN)
        .addValidContext(EDITOR_TABS)
        .ignoreAllContext()
        .addAction(EDITOR_TABS, target -> {
            EditorTab editorTab = Services.EDITOR_TAB_MANAGER.getTabAt(target);
            if (editorTab != null) {
                Services.EDITOR_TAB_MANAGER.closeOthers(editorTab);
            }
        })
        .build());

    public static void initialize() {
        CLOSE_EDITOR_TAB.resetKeys();
        CLOSE_OTHER_EDITOR_TABS.resetKeys();
    }

    private static Keybind registerProjectExplorerKeybind(
        String id, KeyCode keyCode,
        Consumer<ProjectExplorerPane> action,
        KeyCombination.Modifier... modifiers) {
        Keybind keybind = KeybindHandler.registerKeybind(Keybind.builder()
            .id(id)
            .category(GENERAL)
            .addDefaultKey(keyCode, modifiers)
            .addAction(PROJECT_EXPLORER, _ -> action.accept(projectExplorer()))
            .build());
        keybind.resetKeys();
        return keybind;
    }

    private static Keybind registerViewModeKeybind(String id, WorkspaceMode viewMode, KeyCode keyCode) {
        Keybind keybind = KeybindHandler.registerKeybind(Keybind.builder()
            .id(id)
            .category(VIEW_MODES)
            .addDefaultKey(keyCode, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN)
            .addAction(IDE, _ -> Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
                .ifPresent(idePane -> idePane.requestViewMode(viewMode)))
            .build());
        keybind.resetKeys();
        return keybind;
    }

    private static ProjectExplorerPane projectExplorer() {
        return Services.UI_MANAGER.lookupOrThrow(UIIds.IDE.PROJECT_EXPLORER);
    }
}
