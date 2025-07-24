package dev.railroadide.core.settings.keybinds;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;

public record KeybindData(KeyCode keyCode, KeyCombination.Modifier[] modifiers) {
}
