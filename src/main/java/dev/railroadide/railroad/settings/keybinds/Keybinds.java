package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.ide.projectexplorer.PathItem;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.util.Pair;

import java.util.List;

public class Keybinds {
    public static final Keybind COPY = KeybindHandler.registerKeybind(new Keybind(
            List.of(new Pair<>(KeyCode.P, List.of(KeyCombination.CONTROL_DOWN).toArray(KeyCombination.Modifier[]::new))),
            List.of(KeybindContexts.of("railroad:project_tree"))));

    public static void initialize() {
        COPY.addAction(KeybindContexts.of("railroad:project_tree"), node -> {
            @SuppressWarnings("unchecked")
            TreeView<PathItem> tree = (TreeView<PathItem>) node;
            ProjectExplorerPane.copy(tree.getSelectionModel().getSelectedItem().getValue());
        });
    }
}
