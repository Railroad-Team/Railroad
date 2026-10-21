package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps mouse movement while a button is held for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MouseDraggedEvent(MouseEvent event) implements GenericMouseEvent {
}
