package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.KeyEvent;

/**
 * Wraps typed character input for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record KeyTypedEvent(KeyEvent event) implements GenericKeyEvent {
}
