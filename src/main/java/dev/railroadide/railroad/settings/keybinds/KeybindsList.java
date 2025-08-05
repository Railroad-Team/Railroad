package dev.railroadide.railroad.settings.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.core.settings.keybinds.KeybindCategory;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import io.github.palexdev.mfxcore.builders.InsetsBuilder;
import javafx.geometry.Insets;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import lombok.Getter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeybindsList extends TreeView {
    @Getter
    private final Map<String, List<KeybindData>> keybinds = new HashMap<>();

    public KeybindsList(Map<String, List<KeybindData>> map) {
        super(new TreeItem<>(new RRHBox()));
        loadKeybinds(map);
        this.setShowRoot(false);

        TreeItem<RRHBox> root = this.getRoot();
        root.setExpanded(true); // Expand

        refreshTree();
    }

    private void refreshTree() {
        TreeItem<RRHBox> root = this.getRoot();

        List<String> expandedCategoryIds = new ArrayList<>();
        for (TreeItem<RRHBox> item : root.getChildren()) {
            if (item.isExpanded() &&
                    item.getValue().getChildren().get(0) instanceof LocalizedLabel label) {
                expandedCategoryIds.add(label.getId());
            }
        }

        root.getChildren().clear();

        for (Map.Entry<String, List<KeybindData>> entry : keybinds.entrySet()) {
            KeybindCategory category = KeybindHandler.getKeybind(entry.getKey()).getCategory();

            TreeItem<RRHBox> categoryItem = root.getChildren().stream()
                    .filter(item -> item.getValue().getChildren().get(0) instanceof LocalizedLabel label &&
                            label.getId().equals(category.id()))
                    .findFirst()
                    .orElseGet(() -> {
                        var categoryHeader = new LocalizedLabel(category.titleKey());
                        categoryHeader.setId(category.id());
                        var content = new RRHBox();
                        content.getChildren().add(categoryHeader);
                        var newCategoryItem = new TreeItem<>(content);
                        root.getChildren().add(newCategoryItem);
                        return newCategoryItem;
                    });

            categoryItem.getChildren().add(createKeybindTreeItem(entry.getKey(), entry.getValue()));
        }

        for (TreeItem<RRHBox> item : root.getChildren()) {
            if (item.getValue().getChildren().get(0) instanceof LocalizedLabel label &&
                    expandedCategoryIds.contains(label.getId())) {
                item.setExpanded(true);
            }
        }
    }

    private TreeItem<RRHBox> createKeybindTreeItem(String id, List<KeybindData> keybinds) {
        var titleBox = new RRHBox();
        var configBox = new RRVBox();

        var localeKey = "railroad.settings.keybinds." + id.split(":")[1];

        titleBox.getChildren().addAll(new LocalizedLabel(localeKey));

        var addButton = new RRButton("", FontAwesomeSolid.PLUS);
        addButton.setPadding(InsetsBuilder.all(5));
        addButton.getStyleClass().add("square-button");
        addButton.setVariant(RRButton.ButtonVariant.PRIMARY);
        addButton.setOnAction(e -> {
            KeybindData newKeybind = new KeybindData(KeyCode.UNDEFINED, new KeyCombination.Modifier[0]);
            keybinds.add(newKeybind);
            refreshTree();
        });

        titleBox.getChildren().add(addButton);

        for (KeybindData keybind : keybinds) {
            RRHBox keybindBox = new RRHBox(5);
            var keyComboNode = new KeyComboNode(keybind);
            keyComboNode.setOnComboModified((keybindData) -> {
                int index = keybinds.indexOf(keybind);
                if (index != -1 && !keybindData.equals(keybind)) {
                    keybinds.set(index, keybindData);
                }
            });

            RRHBox buttonBox = new RRHBox(5);

            var removeButton = new RRButton("", FontAwesomeSolid.MINUS);
            removeButton.setVariant(RRButton.ButtonVariant.DANGER);
            removeButton.getStyleClass().add("square-button");
            removeButton.setOnAction(e -> {
                keybinds.remove(keybinds.get(this.keybinds.get(id).indexOf(keybind)));
                refreshTree();
            });

            var editButton = new RRButton("", FontAwesomeSolid.PENCIL_ALT);
            editButton.setVariant(RRButton.ButtonVariant.SECONDARY);
            editButton.getStyleClass().add("square-button");
            editButton.setOnAction(e -> {;
                keyComboNode.toggleEditing();
            });

            buttonBox.getChildren().addAll(removeButton, editButton);
            keybindBox.getChildren().addAll(keyComboNode, buttonBox);

            configBox.getChildren().addAll(keybindBox);
        }

        RRHBox treeNodeContent = new RRHBox();
        treeNodeContent.getChildren().addAll(titleBox, configBox);
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
            if (KeybindHandler.getKeybind(id) == null) {
                Railroad.LOGGER.warn("Keybind " + id + " does not exist");
                continue;
            }
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
