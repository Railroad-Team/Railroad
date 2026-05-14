package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Document;

/**
 * Represents an event related to file operations within the Railroad IDE.
 * This event is used to notify subscribers about various file-related actions such as opening, closing,
 * saving, deleting, activating, and deactivating files.
 */
public record DocumentEvent(Document file, EventType eventType) implements GenericDocumentEvent {
    /**
     * Constructs a new FileEvent.
     *
     * @param file  The file associated with this event. Must not be null.
     * @param eventType The type of file event (e.g., OPENED, CLOSED, SAVED, etc.). Must not be null.
     * @throws IllegalArgumentException if file or eventType is null.
     */
    public DocumentEvent {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        if (eventType == null)
            throw new IllegalArgumentException("eventType cannot be null");
    }

    /**
     * Checks if the event is related to a file being opened.
     *
     * @return true if the event type is OPENED, false otherwise.
     */
    public boolean isOpenedEvent() {
        return eventType == EventType.OPENED;
    }

    /**
     * Checks if the event is related to a file being closed.
     *
     * @return true if the event type is CLOSED, false otherwise.
     */
    public boolean isClosedEvent() {
        return eventType == EventType.CLOSED;
    }

    /**
     * Checks if the event is related to a file being saved.
     *
     * @return true if the event type is SAVED, false otherwise.
     */
    public boolean isSavedEvent() {
        return eventType == EventType.SAVED;
    }

    /**
     * Checks if the event is related to a file being deleted.
     *
     * @return true if the event type is DELETED, false otherwise.
     */
    public boolean isDeletedEvent() {
        return eventType == EventType.DELETED;
    }

    /**
     * Checks if the event is related to a file being activated.
     *
     * @return true if the event type is ACTIVATED, false otherwise.
     */
    public boolean isActivatedEvent() {
        return eventType == EventType.ACTIVATED;
    }

    /**
     * Checks if the event is related to a file being deactivated.
     *
     * @return true if the event type is DEACTIVATED, false otherwise.
     */
    public boolean isDeactivatedEvent() {
        return eventType == EventType.DEACTIVATED;
    }

    /**
     * Enum representing the different types of file events.
     */
    public enum EventType {
        OPENED,
        CLOSED,
        SAVED,
        DELETED,
        ACTIVATED,
        DEACTIVATED
    }
}
