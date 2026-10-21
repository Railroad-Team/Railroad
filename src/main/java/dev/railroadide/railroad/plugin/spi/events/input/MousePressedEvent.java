package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps a mouse button press for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MousePressedEvent(MouseEvent event) implements GenericMouseEvent {
}
