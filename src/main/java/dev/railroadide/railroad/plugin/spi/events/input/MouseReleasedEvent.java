package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps a mouse button release for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MouseReleasedEvent(MouseEvent event) implements GenericMouseEvent {
}
