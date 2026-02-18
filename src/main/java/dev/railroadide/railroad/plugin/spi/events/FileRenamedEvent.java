package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.event.Event;

/**
 * Represents an event that is triggered when a file is renamed in the Railroad IDE.
 * This event contains the file that was renamed, along with its old and new name.
 */
public record FileRenamedEvent(Document file, String oldName, String newName) implements Event {
    /**
     * Constructs a new FileRenamedEvent.
     *
     * @param file     The file associated with this event. Must not be null.
     * @param oldName  The previous name of the file. Must not be null.
     * @param newName  The new name of the file. Must not be null.
     * @throws IllegalArgumentException if file, oldName, or newName is null.
     */
    public FileRenamedEvent {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        if (oldName == null)
            throw new IllegalArgumentException("oldName cannot be null");

        if (newName == null)
            throw new IllegalArgumentException("newName cannot be null");
    }
}
