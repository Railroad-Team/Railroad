package dev.railroadide.core.settings.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Keybind {
    @Getter
    private final String id;
    @Getter
    private final KeybindCategory category;
    @Getter
    private final List<KeybindData> defaultKeys;
    @Getter
    private final List<KeybindData> keys = new ArrayList<>();
    @Getter
    private final List<KeybindContexts.KeybindContext> validContexts;
    @Getter
    private final Map<KeybindContexts.KeybindContext, Consumer<Node>> actions;

    private Keybind(String id, KeybindCategory category, List<KeybindData> defaultKeys, List<KeybindContexts.KeybindContext> contexts, Map<KeybindContexts.KeybindContext, Consumer<Node>> actions) {
        this.id = id;
        this.category = category;
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
            var keyCode = key.keyCode();
            var modifiers = key.modifiers();

            if (keyCode != keyEvent.getCode()) continue;

            if (modifiers == null) {
                return true;
            }

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

    public void fromJson(JsonArray json) {
        for (JsonElement keyCombo : json) {
            String[] parts = keyCombo.getAsString().split(";");
            KeyCode keyCode = KeyCode.valueOf(parts[0]);

            if (parts.length < 2 || parts[1].isBlank()) {
                addKey(keyCode, (KeyCombination.Modifier) null);
                continue;
            }

            String[] modParts = parts[1].split(",");
            List<KeyCombination.Modifier> modifiers = new ArrayList<>();

            for (String mod : modParts) {
                switch (mod.trim()) {
                    case "Shortcut":
                        modifiers.add(KeyCombination.SHORTCUT_DOWN);
                        break;
                    case "Ctrl":
                        modifiers.add(KeyCombination.CONTROL_DOWN);
                        break;
                    case "Shift":
                        modifiers.add(KeyCombination.SHIFT_DOWN);
                        break;
                    case "Alt":
                        modifiers.add(KeyCombination.ALT_DOWN);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown modifier: " + mod);
                }
            }

            addKey(keyCode, modifiers.toArray(new KeyCombination.Modifier[0]));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private KeybindCategory category;
        private final List<KeybindData> defaultKeys = new ArrayList<>();
        private final List<KeybindContexts.KeybindContext> validContexts = new ArrayList<>();
        private boolean ignoreAll = false;
        private final Map<KeybindContexts.KeybindContext, Consumer<Node>> actions = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder category(KeybindCategory category) {
            this.category = category;
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
            if (id == null || category == null || actions.isEmpty())
                throw new IllegalStateException("Keybind must have an ID, category and at least one action defined.");

            if(!ignoreAll)
                validContexts.add(KeybindContexts.ALL);

            return new Keybind(id, category, defaultKeys, validContexts, actions);
        }
    }
}
