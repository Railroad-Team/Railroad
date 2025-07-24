package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.KeybindData;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class KeyComboNode extends Button {
    @Setter
    private Consumer<KeybindData> onComboModified;

    private KeybindData keybindData;

    private boolean editing = false;

    private KeyCode tempKeyCode = null;
    private KeyCombination.Modifier[] tempModifiers = new KeyCombination.Modifier[0];

    public KeyComboNode(KeybindData keybindData) {
        this.keybindData = keybindData;

        if (keybindData.keyCode() == KeyCode.UNDEFINED) {
            setText("");
        } else {
            updateText();
        }
    }

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

    private void updateText() {
        StringBuilder label = new StringBuilder();
        if (this.keybindData.modifiers() == null || this.keybindData.modifiers().length == 0) {
            label.append(this.keybindData.keyCode());
            setText(label.toString());
            return;
        }
        for (KeyCombination.Modifier mod : this.keybindData.modifiers()) {
            label.append(mod.toString()).append(" + ");
        }
        label.append(this.keybindData.keyCode());
        setText(label.toString());
    }

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