package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps mouse movement without a button held for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MouseMovedEvent(MouseEvent event) implements GenericMouseEvent {
}
