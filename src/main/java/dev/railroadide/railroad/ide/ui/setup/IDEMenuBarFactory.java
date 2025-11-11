package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.RRMenuBar;
import dev.railroadide.core.ui.localized.LocalizedCheckMenuItem;
import dev.railroadide.core.ui.localized.LocalizedMenu;
import dev.railroadide.core.ui.localized.LocalizedMenuItem;
import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.window.WindowManager;
import javafx.application.Platform;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Builds the main IDE menu bar with all menu items, accelerators, and icons.
 */
public final class IDEMenuBarFactory {
    private IDEMenuBarFactory() {
    }

    public static MenuBar create() {
        var newFileItem = new LocalizedMenuItem("railroad.menu.file.new_file");
        newFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FILE));
        newFileItem.setKeybindData(new KeybindData(KeyCode.N, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}));

        var openFileItem = new LocalizedMenuItem("railroad.menu.file.open_file");
        openFileItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        openFileItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));

        var saveItem = new LocalizedMenuItem("railroad.menu.file.save");
        saveItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));

        var saveAsItem = new LocalizedMenuItem("railroad.menu.file.save_as");
        saveAsItem.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));

        var exitItem = new LocalizedMenuItem("railroad.menu.file.exit");
        exitItem.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exitItem.setOnAction(e -> Platform.exit());

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

        var projectExplorerItem = new LocalizedCheckMenuItem("railroad.menu.view.project_explorer", true);
        projectExplorerItem.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER));
        projectExplorerItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN));

        var propertiesItem = new LocalizedCheckMenuItem("railroad.menu.view.properties", true);
        propertiesItem.setGraphic(new FontIcon(FontAwesomeSolid.INFO_CIRCLE));
        propertiesItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN));

        var consoleItem = new LocalizedCheckMenuItem("railroad.menu.view.console", true);
        consoleItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        consoleItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.SHORTCUT_DOWN));

        var fullScreenItem = new LocalizedMenuItem("railroad.menu.view.full_screen");
        fullScreenItem.setGraphic(new FontIcon(FontAwesomeSolid.EXPAND));
        fullScreenItem.setOnAction($ -> WindowManager.toggleFullScreen());

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
        settingsItem.setOnAction($ -> SettingsPane.openSettingsWindow());

        var pluginsItem = new LocalizedMenuItem("railroad.menu.tools.plugins");
        pluginsItem.setGraphic(new FontIcon(FontAwesomeSolid.PUZZLE_PIECE));
        pluginsItem.setOnAction($ -> SettingsPane.openPluginsWindow());

        var terminalItem = new LocalizedMenuItem("railroad.menu.tools.terminal");
        terminalItem.setGraphic(new FontIcon(FontAwesomeSolid.TERMINAL));
        terminalItem.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));

        var fileMenu = new LocalizedMenu("railroad.menu.file");
        fileMenu.getItems().addAll(newFileItem, openFileItem, saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);
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
            projectExplorerItem,
            propertiesItem,
            consoleItem,
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
}
