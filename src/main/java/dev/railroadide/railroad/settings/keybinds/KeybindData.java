package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.railroad.utility.OperatingSystem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Represents a keyboard or mouse binding and its optional modifiers.
 *
 * @param keyCode The keyboard key, or {@code null} for a mouse binding.
 * @param mouseButton The mouse button, or {@code null} for a keyboard binding.
 * @param modifiers An array of KeyCombination.Modifier, which can include SHIFT, CTRL, ALT, etc. Can be empty
 */
public record KeybindData(KeyCode keyCode, MouseButton mouseButton, KeyCombination.Modifier[] modifiers) {
    /**
     * Creates a keyboard binding.
     *
     * @param keyCode the keyboard key
     * @param modifiers optional modifier keys
     */
    public KeybindData(KeyCode keyCode, KeyCombination.Modifier[] modifiers) {
        this(keyCode, null, modifiers);
    }

    /**
     * Creates a mouse binding.
     *
     * @param mouseButton the mouse button
     * @param modifiers optional modifier keys
     */
    public KeybindData(MouseButton mouseButton, KeyCombination.Modifier[] modifiers) {
        this(null, mouseButton, modifiers);
    }

    /**
     * Converts this binding to a JavaFX keyboard combination.
     *
     * @return the keyboard combination, or {@code null} for a mouse binding
     */
    public KeyCodeCombination getKeyCodeCombination() {
        if (keyCode == null)
            return null;
        return modifiers == null || modifiers.length == 0
            ? new KeyCodeCombination(keyCode)
            : new KeyCodeCombination(keyCode, modifiers);
    }

    /**
     * Checks whether a mouse event matches this binding.
     *
     * @param event the mouse event to inspect
     * @return whether the button and modifiers match
     */
    public boolean matches(MouseEvent event) {
        if (mouseButton == null || event.getButton() != mouseButton)
            return false;

        return matchesModifier(KeyCombination.CONTROL_DOWN, !OperatingSystem.isMac(), event.isControlDown())
            && matchesModifier(KeyCombination.ALT_DOWN, false, event.isAltDown())
            && matchesModifier(KeyCombination.SHIFT_DOWN, false, event.isShiftDown())
            && matchesModifier(KeyCombination.META_DOWN, OperatingSystem.isMac(), event.isMetaDown());
    }

    private boolean matchesModifier(KeyCombination.Modifier expected, boolean includeShortcut, boolean down) {
        if (modifiers == null)
            return !down;

        for (KeyCombination.Modifier modifier : modifiers) {
            if (modifier.getKey() == expected.getKey()
                || includeShortcut && modifier.getKey() == KeyCombination.SHORTCUT_DOWN.getKey())
                return switch (modifier.getValue()) {
                    case DOWN -> down;
                    case UP -> !down;
                    case ANY -> true;
                };
        }
        return !down;
    }
}
