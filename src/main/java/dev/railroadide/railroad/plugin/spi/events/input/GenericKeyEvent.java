package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public interface GenericKeyEvent extends GenericInputEvent {
    KeyEvent event();

    default String getCharacter() {
        return event().getCharacter();
    }

    default String getText() {
        return event().getText();
    }

    default KeyCode getCode() {
        return event().getCode();
    }
}
