package dev.railroadide.core.settings.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;

public class Keybind {
    @Getter
    private final String id;
    @Getter
    private final List<KeybindData> defaultKeys;
    @Getter
    private final List<KeybindData> keys = new ArrayList<>();
    @Getter
    private final List<KeybindContexts.KeybindContext> validContexts;
    @Getter
    private final Map<KeybindContexts.KeybindContext, Consumer<Node>> actions;

    private Keybind(String id, List<KeybindData> defaultKeys, List<KeybindContexts.KeybindContext> contexts, Map<KeybindContexts.KeybindContext, Consumer<Node>> actions) {
        this.id = id;
        this.defaultKeys = defaultKeys;
        this.validContexts = contexts;
        this.actions = actions;
    }

    public void addKey(KeyCode keyCode, KeyCombination.Modifier... modifiers) {
        keys.add(new KeybindData(keyCode, modifiers));
    }

    public void removeKey(KeyCode keyCode, KeyCombination.Modifier... modifiers) {
        keys.remove(new KeybindData(keyCode, modifiers));
    }

    public void resetKeys() {
        keys.clear();
        keys.addAll(defaultKeys);
    }

    public boolean matches(KeyEvent keyEvent) {
        for (KeybindData key : keys) {
            KeyCode keyCode = key.keyCode();
            KeyCombination.Modifier[] modifiers = key.modifiers();

            if (keyCode != keyEvent.getCode()) continue;

            for (KeyCombination.Modifier modifier : modifiers) {
                if (!keyEvent.isShortcutDown() && modifier == KeyCombination.SHORTCUT_DOWN) continue;
                if (!keyEvent.isControlDown() && modifier == KeyCombination.CONTROL_DOWN) continue;
                if (!keyEvent.isShiftDown() && modifier == KeyCombination.SHIFT_DOWN) continue;
                if (!keyEvent.isAltDown() && modifier == KeyCombination.ALT_DOWN) continue;
            }

            return true;
        }

        return false;
    }

    public JsonElement toJson() {
        var keyList = new JsonArray();

        for (KeybindData combo : getKeys()) {
            StringBuilder comboString = new StringBuilder(combo.keyCode().toString() + ";");
            for (KeyCombination.Modifier modifier : combo.modifiers()) {
                comboString.append(",").append(modifier.toString());
            }
            comboString.deleteCharAt(comboString.length() - 1);
            keyList.add(comboString.toString());
        }

        return keyList;
    }

    public void fromJson(JsonArray json) {
        for (JsonElement keyCombo : json) {
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

            addKey(keyCode, modifiers);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private List<KeybindData> defaultKeys = new ArrayList<>();
        private List<KeybindContexts.KeybindContext> validContexts = new ArrayList<>();
        private boolean ignoreAll = false;
        private Map<KeybindContexts.KeybindContext, Consumer<Node>> actions = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder addDefaultKey(KeyCode keyCode, KeyCombination.Modifier... modifiers) {
            defaultKeys.add(new KeybindData(keyCode, modifiers));
            return this;
        }

        public Builder addValidContext(KeybindContexts.KeybindContext context) {
            validContexts.add(context);
            return this;
        }

        public Builder ignoreAllContext() {
            this.ignoreAll = true;
            return this;
        }

        public Builder addAction(KeybindContexts.KeybindContext context, Consumer<Node> action) {
            actions.put(context, action);
            return this;
        }

        public Keybind build() {
            if (id == null || actions.isEmpty())
                throw new IllegalStateException("Keybind must have an ID and at least one action defined.");

            if(!ignoreAll)
                validContexts.add(KeybindContexts.ALL);

            return new Keybind(id, defaultKeys, validContexts, actions);
        }
    }
}
