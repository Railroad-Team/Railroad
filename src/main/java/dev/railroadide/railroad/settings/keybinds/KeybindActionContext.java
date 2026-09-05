package dev.railroadide.railroad.settings.keybinds;

import javafx.scene.Node;
import javafx.scene.input.InputEvent;

import java.util.Objects;

/**
 * Describes a matched keybind action invocation.
 *
 * @param keybind the keybind whose input matched
 * @param context the logical context in which the action was dispatched
 * @param binding the specific keyboard or mouse binding that matched
 * @param event the original input event
 * @param target the node the contextual action should operate on
 */
public record KeybindActionContext(
    Keybind keybind,
    KeybindContexts.KeybindContext context,
    KeybindData binding,
    InputEvent event,
    Node target
) {
    public KeybindActionContext {
        Objects.requireNonNull(keybind, "Keybind cannot be null");
        Objects.requireNonNull(context, "Keybind context cannot be null");
        Objects.requireNonNull(binding, "Matched binding cannot be null");
        Objects.requireNonNull(event, "Input event cannot be null");
    }
}
