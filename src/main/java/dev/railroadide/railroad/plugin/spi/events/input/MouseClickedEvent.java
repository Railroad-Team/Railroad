package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps a mouse click for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MouseClickedEvent(MouseEvent event) implements GenericMouseEvent {
}
