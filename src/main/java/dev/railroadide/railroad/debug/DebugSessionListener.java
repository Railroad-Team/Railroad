package dev.railroadide.railroad.debug;

import dev.railroadide.railroad.debug.model.DebugSessionEvent;

/**
 * Receives debugger lifecycle and inspection events on the publishing thread.
 */
@FunctionalInterface
public interface DebugSessionListener {
    /**
     * Handles a debugger event; JavaFX consumers must marshal UI updates to the application thread.
     *
     * @param event event published by the debugger
     */
    void onDebugEvent(DebugSessionEvent event);
}
