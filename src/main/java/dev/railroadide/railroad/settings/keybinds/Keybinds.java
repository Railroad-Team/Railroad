package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.projectexplorer.PathItem;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;

public class Keybinds {
    public static final Keybind COPY = KeybindHandler.registerKeybind(Keybind.builder()
            .id("copy")
            .addDefaultKey(KeyCode.C, KeyCombination.SHORTCUT_DOWN)
            .addAction(KeybindContexts.of("railroad:project_tree"), node -> {
                @SuppressWarnings("unchecked")
                TreeView<PathItem> tree = (TreeView<PathItem>) node;
                ProjectExplorerPane.copy(tree.getSelectionModel().getSelectedItem().getValue());
                Railroad.LOGGER.info("BUTTON PRESSED");
            })
            .build());

    public static void initialize() {}
}
