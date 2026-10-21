package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps detection of a mouse drag gesture for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MouseDragDetectedEvent(MouseEvent event) implements GenericMouseEvent {
}
