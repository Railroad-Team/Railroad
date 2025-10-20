package dev.railroadide.core.settings.keybinds;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Represents a keybind data structure that contains a key code and optional modifiers.
 * @param keyCode The key code for the keybind, e.g., KeyCode.A, KeyCode.ENTER, etc.
 * @param modifiers An array of KeyCombination.Modifier, which can include SHIFT, CTRL, ALT, etc. Can be empty
 */
public record KeybindData(KeyCode keyCode, KeyCombination.Modifier[] modifiers) {
    public KeyCodeCombination getKeyCodeCombination() {
        return modifiers == null || modifiers.length == 0 ?
            new KeyCodeCombination(keyCode) :
            new KeyCodeCombination(keyCode, modifiers);
    }
}
