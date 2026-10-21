package dev.railroadide.railroad.plugin.spi.events.input;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Provides access to the JavaFX keyboard event wrapped by a plugin event. */
public interface GenericKeyEvent extends GenericInputEvent {
    /**
     * Returns the underlying keyboard event.
     *
     * @return wrapped JavaFX key event
     */
    KeyEvent event();

    /**
     * Returns character input from the wrapped event.
     *
     * @return character value supplied by {@link KeyEvent#getCharacter()}
     */
    default String getCharacter() {
        return event().getCharacter();
    }

    /**
     * Returns the key description from the wrapped event.
     *
     * @return text supplied by {@link KeyEvent#getText()}
     */
    default String getText() {
        return event().getText();
    }

    /**
     * Returns the key code from the wrapped event.
     *
     * @return code supplied by {@link KeyEvent#getCode()}
     */
    default KeyCode getCode() {
        return event().getCode();
    }
}
