package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.railroad.utility.OperatingSystem;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class KeyComboNode extends RRButton {
    @Setter
    private Consumer<KeybindData> onComboModified;

    private KeybindData keybindData;

    private boolean editing = false;

    private KeyCode tempKeyCode = null;
    private KeyCombination.Modifier[] tempModifiers = new KeyCombination.Modifier[0];

    /**
     * Creates a new KeyComboNode with the specified keybind data.
     * @param keybindData The keybind data to initialize the node with.
     */
    public KeyComboNode(KeybindData keybindData) {
        this.keybindData = keybindData;
        setVariant(ButtonVariant.PRIMARY);

        if (keybindData.keyCode() == KeyCode.UNDEFINED) {
            setText("");
        } else {
            updateText();
        }
    }

    /**
     * Toggles the editing mode for this KeyComboNode.
     * When enabled, the node will listen for key events to capture a new key combination.
     */
    public void toggleEditing() {
        if (editing) return;

        editing = true;
        setText("[Recording...]");

        Scene scene = getScene();
        if (scene == null) {
            throw new IllegalStateException("KeyComboNode must be attached to a scene.");
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
    }

    /**
     * Called when a key is pressed in editing mode, and then appends the key code and modifiers to the temporary variables,
     * which are then used to update the keybind data when the keys are released.
     */
    private void onKeyPressed(KeyEvent event) {
        event.consume();

        tempKeyCode = event.getCode();
        List<KeyCombination.Modifier> mods = new ArrayList<>();

        if (event.isControlDown()) mods.add(KeyCombination.CONTROL_DOWN);
        if (event.isAltDown()) mods.add(KeyCombination.ALT_DOWN);
        if (event.isShiftDown()) mods.add(KeyCombination.SHIFT_DOWN);
        if (event.isShortcutDown()) mods.add(KeyCombination.SHORTCUT_DOWN);

        tempModifiers = mods.toArray(new KeyCombination.Modifier[0]);
        if (tempModifiers.length == 0) {
            tempModifiers = null;
        }
    }

    /**
     * Called when a key is released in editing mode. It checks if the key combination has changed,
     * and if so, it updates the keybind data and calls the onComboModified consumer if set.
     * It then resets the editing state and updates the node.
     * @param event
     */
    private void onKeyReleased(KeyEvent event) {
        if (!editing || tempKeyCode == null) return;
        event.consume();

        boolean changed = !tempKeyCode.equals(this.keybindData.keyCode()) || !areModifiersEqual(this.keybindData.modifiers(), tempModifiers);

        if (changed && onComboModified != null) {
            onComboModified.accept(new KeybindData(tempKeyCode, tempModifiers));
        }

        this.keybindData = new KeybindData(tempKeyCode, tempModifiers);
        updateText();

        editing = false;
        tempKeyCode = null;
        tempModifiers = null;

        Scene scene = getScene();
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            scene.removeEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
        }
    }

    /**
     * Updates the text of the button to reflect the current keybind data.
     */
    private void updateText() {
        StringBuilder label = new StringBuilder();
        if (this.keybindData.modifiers() == null || this.keybindData.modifiers().length == 0) {
            label.append(this.keybindData.keyCode());
            setText(label.toString());
            return;
        }
        for (KeyCombination.Modifier mod : this.keybindData.modifiers()) {
            label.append(localizeModifier(mod)).append(" + ");
        }
        label.append(this.keybindData.keyCode());
        setText(label.toString());
    }

    /**
     * Converts Modifier names to the correct name depending on the operating system.
     * @param modifier The modifier to convert.
     * @return The localized name of the modifier.
     */
    private String localizeModifier(KeyCombination.Modifier modifier) {
        return switch (modifier.getKey()) {
            case SHORTCUT:
                if (OperatingSystem.CURRENT == OperatingSystem.MAC) {
                    yield "Command";
                } else {
                    yield "Control";
                }
            case ALT:
                if (OperatingSystem.CURRENT == OperatingSystem.MAC) {
                    yield "Option";
                } else {
                    yield "Alt";
                }
            case CONTROL:
                yield "Control";
            case SHIFT:
                yield "Shift";
            default:
                yield modifier.getKey().toString();
        };
    }

    /**
     * Checks if two arrays of KeyCombination.Modifier are equal.
     * @param a Array of modifiers to compare.
     * @param b Array of modifiers to compare against.
     * @return True if both arrays contain the same modifiers, false otherwise.
     */
    private boolean areModifiersEqual(KeyCombination.Modifier[] a, KeyCombination.Modifier[] b) {
        if (a.length != b.length) return false;
        for (KeyCombination.Modifier modA : a) {
            boolean found = false;
            for (KeyCombination.Modifier modB : b) {
                if (modA.equals(modB)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}