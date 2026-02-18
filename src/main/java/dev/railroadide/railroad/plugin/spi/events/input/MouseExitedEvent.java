package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

public record MouseExitedEvent(MouseEvent event) implements GenericMouseEvent {
}
