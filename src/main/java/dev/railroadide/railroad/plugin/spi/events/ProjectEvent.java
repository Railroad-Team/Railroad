package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.Event;

/**
 * Represents an event related to a project, such as when a project is opened or closed.
 * This event is used to notify listeners about changes in the project's state.
 */
public record ProjectEvent(Project project, EventType eventType) implements Event {
    /**
     * Constructs a new ProjectEvent.
     *
     * @param project   The project associated with this event. Must not be null.
     * @param eventType The type of event (OPENED or CLOSED). Must not be null.
     * @throws IllegalArgumentException if project or eventType is null.
     */
    public ProjectEvent {
        if (project == null)
            throw new IllegalArgumentException("Project cannot be null");

        if (eventType == null)
            throw new IllegalArgumentException("EventType cannot be null");
    }

    /**
     * Checks if the project event is of type OPENED.
     *
     * @return true if the event type is OPENED, false otherwise.
     */
    public boolean isOpened() {
        return eventType == EventType.OPENED;
    }

    /**
     * Checks if the project event is of type CLOSED.
     *
     * @return true if the event type is CLOSED, false otherwise.
     */
    public boolean isClosed() {
        return eventType == EventType.CLOSED;
    }

    /**
     * Enum representing the type of project event.
     */
    public enum EventType {
        OPENED,
        CLOSED
    }
}
