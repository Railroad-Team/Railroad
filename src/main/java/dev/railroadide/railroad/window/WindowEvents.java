package dev.railroadide.railroad.window;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.events.input.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Forwards a stage's mouse and keyboard events to the application event bus.
 * Retains the installed handlers so they can be removed together.
 */
public class WindowEvents {
    private final Stage stage;
    private final List<TrackedEventHandler<?>> eventHandlers = new ArrayList<>();

    /**
     * Creates an event tracker without installing any handlers.
     *
     * @param stage the stage whose input events will be forwarded
     */
    public WindowEvents(Stage stage) {
        this.stage = stage;
    }

    /**
     * Installs handlers that publish mouse and keyboard events to the application event bus.
     * Each call installs another set of handlers; call {@link #stopTracking()} before restarting.
     */
    public void beginTracking() {
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> Railroad.EVENT_BUS.publish(new MouseClickedEvent(event)));

        addEventHandler(MouseEvent.MOUSE_MOVED, event -> Railroad.EVENT_BUS.publish(new MouseMovedEvent(event)));

        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> Railroad.EVENT_BUS.publish(new MousePressedEvent(event)));

        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> Railroad.EVENT_BUS.publish(new MouseReleasedEvent(event)));

        addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> Railroad.EVENT_BUS.publish(new MouseDraggedEvent(event)));

        addEventHandler(MouseEvent.MOUSE_ENTERED, event -> Railroad.EVENT_BUS.publish(new MouseEnteredEvent(event)));

        addEventHandler(MouseEvent.MOUSE_EXITED, event -> Railroad.EVENT_BUS.publish(new MouseExitedEvent(event)));

        addEventHandler(MouseEvent.DRAG_DETECTED,
            event -> Railroad.EVENT_BUS.publish(new MouseDragDetectedEvent(event)));

        addEventHandler(KeyEvent.KEY_PRESSED, event -> Railroad.EVENT_BUS.publish(new KeyPressedEvent(event)));

        addEventHandler(KeyEvent.KEY_RELEASED, event -> Railroad.EVENT_BUS.publish(new KeyReleasedEvent(event)));

        addEventHandler(KeyEvent.KEY_TYPED, event -> Railroad.EVENT_BUS.publish(new KeyTypedEvent(event)));
    }

    private <T extends Event> void addEventHandler(EventType<T> eventType, EventHandler<? super T> handler) {
        stage.addEventHandler(eventType, handler);
        eventHandlers.add(new TrackedEventHandler<>(eventType, handler));
    }

    /**
     * Removes all handlers installed by this tracker and clears the tracked registrations.
     * Calling this method when no handlers are registered has no effect.
     */
    public void stopTracking() {
        for (TrackedEventHandler<?> trackedHandler : eventHandlers) {
            trackedHandler.unregister(stage);
        }

        eventHandlers.clear();
    }

    private record TrackedEventHandler<T extends Event>(EventType<T> eventType, EventHandler<? super T> handler) {
        private void unregister(Stage stage) {
            stage.removeEventHandler(eventType, handler);
        }
    }
}
