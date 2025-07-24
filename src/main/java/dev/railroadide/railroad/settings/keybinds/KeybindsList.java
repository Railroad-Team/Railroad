package dev.railroadide.railroad.settings.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.core.settings.keybinds.KeybindData;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;

import java.util.*;

public class KeybindsList extends VBox {
    private final Map<String, List<KeybindData>> keybinds = new HashMap<>();
    public KeybindsList(Map<String, List<KeybindData>> map) {
        super();

        loadKeybinds(map);

        for (Map.Entry<String, List<KeybindData>> entry : keybinds.entrySet()) {
            Label title = new Label(entry.getKey());
            ComboBox<KeyCode> keySelector = new ComboBox<>();
            keySelector.getItems().addAll(KeyCode.values());
            keySelector.getSelectionModel().select(entry.getValue().getFirst().keyCode());
            Button test = new Button("Accept");
            test.setOnAction(e -> {
                keybinds.get(entry.getKey()).set(0, new KeybindData(keySelector.getValue(), new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}));
            });
            this.getChildren().addAll(title, keySelector, test);
        }
    }

    public Map<String, List<KeybindData>> getKeybinds() {
        return keybinds;
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
                StringBuilder comboString = new StringBuilder(combo.keyCode().toString() + ";");
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
            String key = keybindJson.getKey();
            JsonArray keyList = keybindJson.getValue().getAsJsonArray();
            KeybindHandler.KEYBIND_REGISTRY.get(key).fromJson(keyList);

            List<KeybindData> keyCombos;

            for (JsonElement keyCombo : keyList) {
                String[] parts = keyCombo.getAsString().split(";");
                KeyCode keyCode = KeyCode.valueOf(parts[0]);
                KeyCombination.Modifier[] modifiers = new KeyCombination.Modifier[parts.length - 1];

                var modParts = parts[1].split(",");

                for (int i = 0; i < modParts.length; i++) {
                    switch (modParts[i]) {
                        case "Shortcut":
                            modifiers[i] = KeyCombination.SHORTCUT_DOWN;
                            break;
                        case "Control":
                            modifiers[i] = KeyCombination.CONTROL_DOWN;
                            break;
                        case "Shift":
                            modifiers[i] = KeyCombination.SHIFT_DOWN;
                            break;
                        case "Alt":
                            modifiers[i] = KeyCombination.ALT_DOWN;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown modifier: " + Arrays.toString(modParts));
                    }
                }

                var l = new ArrayList<KeybindData>();
                l.add(new KeybindData(keyCode, modifiers));

                map.put(key, l);
            }
        }

        return map;
    }
}
