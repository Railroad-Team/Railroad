package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.localization.L18n;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class KeyComboNode extends RRButton {
    @Setter
    private Consumer<KeybindData> onComboModified;
    private KeybindData keybindData;

    private boolean editing = false;
    private KeyCode pendingKeyCode;
    private KeyCombination.Modifier[] pendingModifiers = new KeyCombination.Modifier[0];

    public KeyComboNode(KeybindData data) {
        super("");
        this.keybindData = data == null
            ? new KeybindData(KeyCode.UNDEFINED, new KeyCombination.Modifier[0])
            : data;

        setVariant(ButtonVariant.SECONDARY);
        setButtonSize(ButtonSize.SMALL);
        getStyleClass().add("keybind-shortcut-chip");
        updateLabel();
    }

    public void toggleEditing() {
        if (editing) {
            return;
        }

        Scene scene = getScene();
        if (scene == null) {
            throw new IllegalStateException("KeyComboNode must be attached to a Scene before editing.");
        }

        editing = true;
        getStyleClass().add("recording");
        setText(L18n.localize("railroad.settings.keybinds.recording"));

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyReleased);
    }

    private void handleKeyPressed(KeyEvent event) {
        event.consume();
        pendingKeyCode = event.getCode();

        List<KeyCombination.Modifier> modifiers = new ArrayList<>();
        if (event.isShortcutDown()) modifiers.add(KeyCombination.SHORTCUT_DOWN);
        if (event.isControlDown()) modifiers.add(KeyCombination.CONTROL_DOWN);
        if (event.isAltDown()) modifiers.add(KeyCombination.ALT_DOWN);
        if (event.isShiftDown()) modifiers.add(KeyCombination.SHIFT_DOWN);

        pendingModifiers = modifiers.isEmpty() ? null : modifiers.toArray(new KeyCombination.Modifier[0]);
    }

    private void handleKeyReleased(KeyEvent event) {
        if (!editing || pendingKeyCode == null) {
            return;
        }

        event.consume();

        if (pendingKeyCode.isModifierKey()) {
            return; // Wait for a non-modifier key before finalizing
        }

        KeybindData updated = new KeybindData(pendingKeyCode, pendingModifiers);
        boolean changed = !Objects.equals(keybindData.keyCode(), updated.keyCode())
            || !Arrays.equals(keybindData.modifiers(), updated.modifiers());

        if (changed && onComboModified != null) {
            onComboModified.accept(updated);
        }

        keybindData = updated;
        finishEditing(event.getSource());
    }

    private void finishEditing(Object source) {
        editing = false;
        pendingKeyCode = null;
        pendingModifiers = null;

        getStyleClass().remove("recording");
        updateLabel();

        Scene scene = getScene();
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
            scene.removeEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyReleased);
        }
    }

    private void updateLabel() {
        if (keybindData.keyCode() == null || keybindData.keyCode() == KeyCode.UNDEFINED) {
            setText(L18n.localize("railroad.settings.keybinds.click_to_record"));
            return;
        }

        StringBuilder label = new StringBuilder();
        KeyCombination.Modifier[] modifiers = keybindData.modifiers();
        if (modifiers != null) {
            for (KeyCombination.Modifier modifier : modifiers) {
                label.append(localizeModifier(modifier)).append(" + ");
            }
        }
        label.append(keybindData.keyCode().getName());
        setText(label.toString());
    }

    private String localizeModifier(KeyCombination.Modifier modifier) {
        return switch (modifier.getKey()) {
            case SHORTCUT -> OperatingSystem.isMac() ? "⌘" : "Ctrl";
            case CONTROL -> "Ctrl";
            case ALT -> OperatingSystem.isMac() ? "⌥" : "Alt";
            case SHIFT -> "Shift";
            default -> modifier.getKey().getName();
        };
    }
}
