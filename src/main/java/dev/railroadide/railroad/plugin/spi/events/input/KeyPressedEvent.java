package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.KeyEvent;

public record KeyPressedEvent(KeyEvent event) implements GenericKeyEvent {
}
