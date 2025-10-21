package dev.railroadide.core.settings.keybinds;

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

    /**
     * Creates a Keybind instance. Should not be called directly. See {@link Keybind.Builder}
     * @param id The id of the keybind. Used for the localization key and to save inside settings.json
     * @param category The category of the keybind. Used to group keybinds in the settings menu.
     * @param defaultKeys The default key(s) for this keybind. These each contain a KeyCode and an optional array of Modifiers.
     * @param contexts The contexts in which this keybind is valid.
     * @param actions A map of contexts to actions, when the keybind is pressed in that context, the corresponding action will be executed.
     */
    private Keybind(String id, KeybindCategory category, List<KeybindData> defaultKeys, List<KeybindContexts.KeybindContext> contexts, Map<KeybindContexts.KeybindContext, Consumer<Node>> actions) {
        this.id = id;
        this.category = category;
        this.defaultKeys = defaultKeys;
        this.validContexts = contexts;
        this.actions = actions;
    }

    /**
     * Adds a key combination to the keybind with the given KeyCode and optional modifiers.
     * @param keyCode The primary key code for the keybind, e.g., KeyCode.A, KeyCode.ENTER, etc.
     * @param modifiers Optional modifiers for the keybind, such as Control, Shift, Alt, or Shortcut.
     */
    public void addKey(KeyCode keyCode, KeyCombination.Modifier... modifiers) {
        keys.add(new KeybindData(keyCode, modifiers));
    }

    /**
     * Removes a key combination from the keybind.
     * @param keyCode The primary key code for the keybind, e.g., KeyCode.A, KeyCode.ENTER, etc.
     * @param modifiers Optional modifiers for the keybind, such as Control, Shift, Alt, or Shortcut.
     */
    public void removeKey(KeyCode keyCode, KeyCombination.Modifier... modifiers) {
        keys.remove(new KeybindData(keyCode, modifiers));
    }

    /**
     * Resets the keybind's keys to the default keys.
     * This will clear any custom keys set and restore the keybind to its initial state.
     */
    public void resetKeys() {
        keys.clear();
        keys.addAll(defaultKeys);
    }

    /**
     * Checks if the given KeyEvent matches any of the keybind's keys.
     * @param keyEvent The KeyEvent
     * @return true if the KeyEvent matches any of the keybind's keys, false otherwise.
     */
    public boolean matches(KeyEvent keyEvent) {
        for (KeybindData key : keys) {
            KeyCode keyCode = key.keyCode();
            KeyCombination.Modifier[] modifiers = key.modifiers();

            if (keyCode != keyEvent.getCode()) continue;

            if (modifiers == null) return true;

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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class for creating Keybind instances.
     */
    public static class Builder {
        private String id;
        private KeybindCategory category;
        private final List<KeybindData> defaultKeys = new ArrayList<>();
        private final List<KeybindContexts.KeybindContext> validContexts = new ArrayList<>();
        private boolean ignoreAll = false;
        private final Map<KeybindContexts.KeybindContext, Consumer<Node>> actions = new HashMap<>();

        /**
         * Sets the ID of the keybind. This is used for localization and saving in settings.json.
         * @param id The ID of the keybind.
         * @return the modified Builder instance.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the category of the keybind. This is used to group keybinds in the settings menu.
         * @param category The category of the keybind.
         * @return the modified Builder instance.
         */
        public Builder category(KeybindCategory category) {
            this.category = category;
            return this;
        }

        /**
         * Adds a default key to the keybind. This key will be used if no other keys are set.
         * @param keyCode The primary key code for the keybind.
         * @param modifiers Optional modifiers for the keybind, such as Control, Shift, Alt, or Shortcut.
         * @return the modified Builder instance.
         */
        public Builder addDefaultKey(KeyCode keyCode, KeyCombination.Modifier... modifiers) {
            defaultKeys.add(new KeybindData(keyCode, modifiers));
            return this;
        }

        /**
         * Adds a valid context for the keybind. This context determines where the keybind can be used.
         * @param context The context in which the keybind is valid.
         * @return the modified Builder instance.
         */
        public Builder addValidContext(KeybindContexts.KeybindContext context) {
            validContexts.add(context);
            return this;
        }

        /**
         * Will remove the ALL context from the valid contexts.
         * This means the keybind will not be valid in all contexts, but only in the ones explicitly added.
         * @return the modified Builder instance.
         */
        public Builder ignoreAllContext() {
            this.ignoreAll = true;
            return this;
        }

        /**
         * Adds an action to be executed when the keybind is pressed in a specific context.
         * @param context The context in which the action should be executed.
         * @param action The action to be executed when the keybind is pressed in the specified context.
         * @return the modified Builder instance.
         */
        public Builder addAction(KeybindContexts.KeybindContext context, Consumer<Node> action) {
            actions.put(context, action);
            return this;
        }

        /**
         * Builds the Keybind instance with the provided parameters.
         * @return a new Keybind instance.
         * @throws IllegalStateException if the ID, category, or actions are not set correctly.
         */
        public Keybind build() {
            if (id == null || category == null || actions.isEmpty())
                throw new IllegalStateException("Keybind must have an ID, category and at least one action defined.");

            if(!ignoreAll)
                validContexts.add(KeybindContexts.ALL);

            return new Keybind(id, category, defaultKeys, validContexts, actions);
        }
    }
}
