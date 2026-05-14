package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public interface GenericMouseEvent extends GenericInputEvent {
    MouseEvent event();

    default double getSceneX() {
        return event().getSceneX();
    }

    default double getSceneY() {
        return event().getSceneY();
    }

    default double getScreenX() {
        return event().getScreenX();
    }

    default double getScreenY() {
        return event().getScreenY();
    }

    default MouseButton getButton() {
        return event().getButton();
    }
}
