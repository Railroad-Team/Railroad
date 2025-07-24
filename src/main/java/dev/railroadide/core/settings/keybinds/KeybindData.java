package dev.railroadide.core.settings.keybinds;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import org.jetbrains.annotations.Nullable;

public record KeybindData(KeyCode keyCode, @Nullable KeyCombination.Modifier[] modifiers) {
}
