package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseEvent;

public record MouseReleasedEvent(MouseEvent event) implements GenericMouseEvent {
}
