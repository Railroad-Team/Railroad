package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.KeyEvent;

/**
 * Wraps a key release for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record KeyReleasedEvent(KeyEvent event) implements GenericKeyEvent {
}
