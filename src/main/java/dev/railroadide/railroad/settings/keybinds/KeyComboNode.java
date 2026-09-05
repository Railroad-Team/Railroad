package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.scene.Scene;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
    private MouseButton pendingMouseButton;
    private KeyCombination.Modifier[] pendingModifiers = new KeyCombination.Modifier[0];
    private final EventHandler<KeyEvent> keyPressedHandler = this::handleKeyPressed;
    private final EventHandler<KeyEvent> keyReleasedHandler = this::handleKeyReleased;
    private final EventHandler<MouseEvent> mousePressedHandler = this::handleMousePressed;

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
        if (editing)
            return;

        Scene scene = getScene();
        if (scene == null)
            throw new IllegalStateException("KeyComboNode must be attached to a Scene before editing.");

        editing = true;
        getStyleClass().add("recording");
        setText(L18n.localize("railroad.settings.keybinds.recording"));

        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyPressedHandler);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
    }

    private void handleKeyPressed(KeyEvent event) {
        event.consume();
        pendingKeyCode = event.getCode();
        pendingMouseButton = null;
        pendingModifiers = collectModifiers(
            event.isShortcutDown(),
            event.isControlDown(),
            event.isAltDown(),
            event.isShiftDown(),
            event.isMetaDown());
    }

    private void handleMousePressed(MouseEvent event) {
        if (!editing || event.getButton() == MouseButton.NONE)
            return;

        event.consume();
        pendingKeyCode = null;
        pendingMouseButton = event.getButton();
        pendingModifiers = collectModifiers(
            event.isShortcutDown(),
            event.isControlDown(),
            event.isAltDown(),
            event.isShiftDown(),
            event.isMetaDown());

        commit(new KeybindData(pendingMouseButton, pendingModifiers));
    }

    private static KeyCombination.Modifier[] collectModifiers(
        boolean shortcutDown,
        boolean controlDown,
        boolean altDown,
        boolean shiftDown,
        boolean metaDown
    ) {
        List<KeyCombination.Modifier> modifiers = new ArrayList<>();
        if (shortcutDown) {
            modifiers.add(KeyCombination.SHORTCUT_DOWN);
        }
        if (controlDown) {
            modifiers.add(KeyCombination.CONTROL_DOWN);
        }
        if (altDown) {
            modifiers.add(KeyCombination.ALT_DOWN);
        }
        if (shiftDown) {
            modifiers.add(KeyCombination.SHIFT_DOWN);
        }
        if (metaDown) {
            modifiers.add(KeyCombination.META_DOWN);
        }

        if (OperatingSystem.isMac()) {
            if (metaDown) {
                modifiers.remove(KeyCombination.SHORTCUT_DOWN);
            }
        } else {
            if (controlDown) {
                modifiers.remove(KeyCombination.SHORTCUT_DOWN);
            }
        }

        return modifiers.isEmpty() ? null : modifiers.toArray(new KeyCombination.Modifier[0]);
    }

    private void handleKeyReleased(KeyEvent event) {
        if (!editing || pendingKeyCode == null)
            return;

        event.consume();

        if (pendingKeyCode.isModifierKey())
            return; // Wait for a non-modifier key before finalizing

        commit(new KeybindData(pendingKeyCode, pendingModifiers));
    }

    private void commit(KeybindData updated) {
        boolean changed = !Objects.equals(keybindData.keyCode(), updated.keyCode())
            || !Objects.equals(keybindData.mouseButton(), updated.mouseButton())
            || !Arrays.equals(keybindData.modifiers(), updated.modifiers());

        if (changed && onComboModified != null) {
            onComboModified.accept(updated);
        }

        keybindData = updated;
        finishEditing();
    }

    private void finishEditing() {
        editing = false;
        pendingKeyCode = null;
        pendingMouseButton = null;
        pendingModifiers = null;

        getStyleClass().remove("recording");
        updateLabel();

        Scene scene = getScene();
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyPressedHandler);
            scene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleasedHandler);
            scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        }
    }

    private void updateLabel() {
        if ((keybindData.keyCode() == null || keybindData.keyCode() == KeyCode.UNDEFINED)
            && keybindData.mouseButton() == null) {
            setText(L18n.localize("railroad.settings.keybinds.click_to_record"));
            return;
        }

        var label = new StringBuilder();
        KeyCombination.Modifier[] modifiers = keybindData.modifiers();
        if (modifiers != null) {
            for (KeyCombination.Modifier modifier : modifiers) {
                label.append(localizeModifier(modifier)).append(" + ");
            }
        }
        if (keybindData.mouseButton() != null) {
            label.append(mouseButtonName(keybindData.mouseButton()));
        } else {
            label.append(keybindData.keyCode().getName());
        }
        setText(label.toString());
    }

    private static String mouseButtonName(MouseButton mouseButton) {
        String localizationKey = switch (mouseButton) {
            case PRIMARY -> "railroad.settings.keybinds.mouse_button.primary";
            case MIDDLE -> "railroad.settings.keybinds.mouse_button.middle";
            case SECONDARY -> "railroad.settings.keybinds.mouse_button.secondary";
            case BACK -> "railroad.settings.keybinds.mouse_button.back";
            case FORWARD -> "railroad.settings.keybinds.mouse_button.forward";
            case NONE -> "railroad.settings.keybinds.mouse_button.none";
        };
        return L18n.localize(localizationKey);
    }

    private String localizeModifier(KeyCombination.Modifier modifier) {
        return switch (modifier.getKey()) {
            case SHORTCUT -> OperatingSystem.isMac() ? "⌘" : "Ctrl";
            case META -> "⌘";
            case CONTROL -> "Ctrl";
            case ALT -> OperatingSystem.isMac() ? "⌥" : "Alt";
            case SHIFT -> "Shift";
            default -> modifier.getKey().getName();
        };
    }
}
