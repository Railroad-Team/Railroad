package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

public record MouseEnteredEvent(MouseEvent event) implements GenericMouseEvent {
}
