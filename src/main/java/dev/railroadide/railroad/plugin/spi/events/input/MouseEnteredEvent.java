package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

/**
 * Wraps the mouse entering a target for plugin event subscribers.
 *
 * @param event underlying JavaFX input event
 */
public record MouseEnteredEvent(MouseEvent event) implements GenericMouseEvent {
}
