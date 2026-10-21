package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.KeyEvent;

/**
 * Wraps a key press for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record KeyPressedEvent(KeyEvent event) implements GenericKeyEvent {
}
