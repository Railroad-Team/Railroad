package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/** Provides access to the JavaFX mouse event wrapped by a plugin event. */
public interface GenericMouseEvent extends GenericInputEvent {
    /**
     * Returns the underlying mouse event.
     *
     * @return wrapped JavaFX mouse event
     */
    MouseEvent event();

    /**
     * Returns the mouse's horizontal position in the scene.
     *
     * @return scene-relative X coordinate from the wrapped event
     */
    default double getSceneX() {
        return event().getSceneX();
    }

    /**
     * Returns the mouse's vertical position in the scene.
     *
     * @return scene-relative Y coordinate from the wrapped event
     */
    default double getSceneY() {
        return event().getSceneY();
    }

    /**
     * Returns the mouse's horizontal position on the screen.
     *
     * @return screen-relative X coordinate from the wrapped event
     */
    default double getScreenX() {
        return event().getScreenX();
    }

    /**
     * Returns the mouse's vertical position on the screen.
     *
     * @return screen-relative Y coordinate from the wrapped event
     */
    default double getScreenY() {
        return event().getScreenY();
    }

    /**
     * Returns the button associated with the wrapped event.
     *
     * @return mouse button supplied by {@link MouseEvent#getButton()}
     */
    default MouseButton getButton() {
        return event().getButton();
    }
}
