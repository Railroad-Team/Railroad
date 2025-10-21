package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindCategory;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.ide.projectexplorer.PathItem;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;

public class Keybinds {
    public static final Keybind COPY = KeybindHandler.registerKeybind(Keybind.builder()
            .id("railroad:copy")
            .category(new KeybindCategory("railroad:general", "railroad.settings.keybinds.category.general"))
            .addDefaultKey(KeyCode.C, KeyCombination.SHORTCUT_DOWN)
            .addAction(KeybindContexts.of("railroad:project_explorer"), node -> {
                @SuppressWarnings("unchecked")
                TreeView<PathItem> tree = (TreeView<PathItem>) node;
                ProjectExplorerPane.copy(tree.getSelectionModel().getSelectedItem().getValue());
            })
            .build());

    public static final Keybind PASTE = KeybindHandler.registerKeybind(Keybind.builder()
            .id("railroad:paste")
            .category(new KeybindCategory("railroad:general", "railroad.settings.keybinds.category.general"))
            .addDefaultKey(KeyCode.V, KeyCombination.SHORTCUT_DOWN)
            .addAction(KeybindContexts.of("railroad:project_explorer"), node -> {
                @SuppressWarnings("unchecked")
                TreeView<PathItem> tree = (TreeView<PathItem>) node;
                ProjectExplorerPane.paste(tree.getSelectionModel().getSelectedItem().getValue());
            })
            .build());

    public static void initialize() {
    }
}
