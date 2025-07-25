package dev.railroadide.railroad.settings.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.core.settings.keybinds.KeybindCategory;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeybindsList extends TreeView {
    @Getter
    private final Map<String, List<KeybindData>> keybinds = new HashMap<>();

    public KeybindsList(Map<String, List<KeybindData>> map) {
        super(new TreeItem<>(new HBox()));
        loadKeybinds(map);
        this.setShowRoot(false);

        TreeItem<VBox> root = this.getRoot();
        root.setExpanded(true); // Expand

        refreshTree();
    }

    private void refreshTree() {
        TreeItem<VBox> root = this.getRoot();

        List<String> expandedCategoryIds = new ArrayList<>();
        for (TreeItem<VBox> item : root.getChildren()) {
            if (item.isExpanded() &&
                    item.getValue().getChildren().get(0) instanceof LocalizedLabel label) {
                expandedCategoryIds.add(label.getId());
            }
        }

        root.getChildren().clear();

        for (Map.Entry<String, List<KeybindData>> entry : keybinds.entrySet()) {
            KeybindCategory category = KeybindHandler.getKeybind(entry.getKey()).getCategory();

            TreeItem<VBox> categoryItem = root.getChildren().stream()
                    .filter(item -> item.getValue().getChildren().get(0) instanceof LocalizedLabel label &&
                            label.getId().equals(category.id()))
                    .findFirst()
                    .orElseGet(() -> {
                        var categoryHeader = new LocalizedLabel(category.titleKey());
                        categoryHeader.setId(category.id());
                        var newCategoryItem = new TreeItem<>(new VBox(categoryHeader));
                        root.getChildren().add(newCategoryItem);
                        return newCategoryItem;
                    });

            categoryItem.getChildren().add(createKeybindTreeItem(entry.getKey(), entry.getValue()));
        }

        for (TreeItem<VBox> item : root.getChildren()) {
            if (item.getValue().getChildren().get(0) instanceof LocalizedLabel label &&
                    expandedCategoryIds.contains(label.getId())) {
                item.setExpanded(true);
            }
        }
    }

    private TreeItem<VBox> createKeybindTreeItem(String id, List<KeybindData> keybinds) {
        //TODO make it look good
        var titleBox = new HBox();
        var configBox = new VBox();

        titleBox.getChildren().addAll(new LocalizedLabel(id));

        var addButton = new Button();
        addButton.setText("+"); // TODO use FontAwesome icon
        addButton.setOnAction(e -> {
            KeybindData newKeybind = new KeybindData(KeyCode.UNDEFINED, new KeyCombination.Modifier[0]);
            keybinds.add(newKeybind);
            refreshTree();
        });

        titleBox.getChildren().add(addButton);

        for (KeybindData keybind : keybinds) {
            HBox keybindBox = new HBox(5);
            var keyComboNode = new KeyComboNode(keybind);
            keyComboNode.setOnComboModified((keybindData) -> {
                int index = keybinds.indexOf(keybind);
                if (index != -1 && !keybindData.equals(keybind)) {
                    keybinds.set(index, keybindData);
                }
            });

            HBox buttonBox = new HBox(5);

            var removeButton = new Button();
            removeButton.setText("-");
            removeButton.setOnAction(e -> {
                keybinds.remove(keybinds.get(this.keybinds.get(id).indexOf(keybind)));
                refreshTree();
            });

            var editButton = new Button();
            editButton.setText("✏️"); // TODO FontAwesome
            editButton.setOnAction(e -> {;
                keyComboNode.toggleEditing();
            });

            buttonBox.getChildren().addAll(removeButton, editButton);
            keybindBox.getChildren().addAll(keyComboNode, buttonBox);

            configBox.getChildren().addAll(keybindBox);
        }

        VBox treeNodeContent = new VBox(titleBox, configBox);
        return new TreeItem<>(treeNodeContent);
    }

    public void loadKeybinds(Map<String, List<KeybindData>> keybinds) {
        this.keybinds.clear();
        this.keybinds.putAll(keybinds);
    }

    public static JsonElement toJson(Map<String, List<KeybindData>> keybinds) {
        var jsonObject = new JsonObject();
        for (Map.Entry<String, List<KeybindData>> entry : keybinds.entrySet()) {
            var keyList = new JsonArray();

            for (KeybindData combo : entry.getValue()) {
                var comboString = new StringBuilder(combo.keyCode().toString() + ";");
                if (combo.modifiers() == null || combo.modifiers().length == 0) {
                    keyList.add(comboString.toString());
                    continue;
                }
                for (KeyCombination.Modifier modifier : combo.modifiers()) {
                    comboString.append(modifier.toString()).append(",");
                }
                comboString.deleteCharAt(comboString.length() - 1);
                keyList.add(comboString.toString());
            }

            jsonObject.add(entry.getKey(), keyList);
        }

        return jsonObject;
    }

    public static Map<String, List<KeybindData>> fromJson(JsonElement json) {
        var map = new HashMap<String, List<KeybindData>>();

        for (Map.Entry<String, JsonElement> keybindJson : json.getAsJsonObject().entrySet()) {
            var id = keybindJson.getKey();
            var keyList = keybindJson.getValue().getAsJsonArray();
            KeybindHandler.getKeybind(id).fromJson(keyList);

            var keys = new ArrayList<KeybindData>();
            for (JsonElement keyCombo : keyList) {
                var parts = keyCombo.getAsString().split(";");
                var keyCode = KeyCode.valueOf(parts[0]);

                if (parts.length == 1) {
                    keys.add(new KeybindData(keyCode, new KeyCombination.Modifier[0]));
                    continue;
                }

                var modParts = parts[1].split(",");
                var modifiers = new KeyCombination.Modifier[modParts.length];

                for (int i = 0; i < modParts.length; i++) {
                    switch (modParts[i]) {
                        case "Shortcut":
                            modifiers[i] = KeyCombination.SHORTCUT_DOWN;
                            break;
                        case "Ctrl":
                            modifiers[i] = KeyCombination.CONTROL_DOWN;
                            break;
                        case "Shift":
                            modifiers[i] = KeyCombination.SHIFT_DOWN;
                            break;
                        case "Alt":
                            modifiers[i] = KeyCombination.ALT_DOWN;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown modifier: " + modParts[i]);
                    }
                }

                keys.add(new KeybindData(keyCode, modifiers));
            }

            map.put(id, keys);
        }

        return map;
    }
}
