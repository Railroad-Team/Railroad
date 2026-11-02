package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;

/**
 * Represents an event that is triggered when a file is renamed in the Railroad IDE.
 * This event contains the file that was renamed, along with its old and new name.
 *
 * @param file The file associated with this event. Must not be null.
 * @param oldName The previous name of the file. Must not be null.
 * @param newName The new name of the file. Must not be null.
 * @param previousUri original location, including the original directory when moving a file
 */
public record DocumentRenamedEvent(Document file, String oldName, String newName, DocumentUri previousUri)
    implements
        GenericDocumentEvent {
    /**
     * Creates a rename event within the file's current directory for existing callers.
     *
     * @param file renamed document
     * @param oldName previous file name
     * @param newName new file name
     */
    public DocumentRenamedEvent(Document file, String oldName, String newName) {
        this(file, oldName, newName, file == null || oldName == null
            ? null
            : DocumentUri.fromPath(file.getPath().resolveSibling(oldName)));
    }
    /**
     * Constructs a new DocumentRenamedEvent.
     *
     * @param file The file associated with this event. Must not be null.
     * @param oldName The previous name of the file. Must not be null.
     * @param newName The new name of the file. Must not be null.
     * @param previousUri original location
     * @throws IllegalArgumentException if file, oldName, or newName is null.
     */
    public DocumentRenamedEvent {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        if (oldName == null)
            throw new IllegalArgumentException("oldName cannot be null");

        if (newName == null)
            throw new IllegalArgumentException("newName cannot be null");
        if (previousUri == null)
            throw new IllegalArgumentException("previousUri cannot be null");
    }
}
