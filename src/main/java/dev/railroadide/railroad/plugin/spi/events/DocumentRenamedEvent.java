package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.plugin.spi.dto.Document;

import java.net.URI;
import java.net.URISyntaxException;

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
            : previousLocation(file.getUri(), oldName));
    }

    private static DocumentUri previousLocation(DocumentUri current, String oldName) {
        if (current.isFile())
            return DocumentUri.fromPath(current.filePath().orElseThrow().resolveSibling(oldName));
        URI address = current.value();
        try {
            if (!address.isOpaque()) {
                String path = address.getPath();
                if (path == null || !path.contains("/"))
                    return current;
                return DocumentUri.parse(new URI(address.getScheme(), address.getAuthority(),
                    path.substring(0, path.lastIndexOf('/') + 1) + oldName,
                    address.getQuery(), address.getFragment()).toString());
            }
            String entry = address.getRawSchemeSpecificPart();
            if (!entry.contains("!/"))
                return current;
            String encodedName = DocumentUri.virtual("rename", oldName).value().getRawPath().substring(1);
            return DocumentUri.parse(address.getScheme() + ":" + entry.substring(0, entry.lastIndexOf('/') + 1)
                + encodedName + (address.getRawFragment() == null ? "" : "#" + address.getRawFragment()));
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid previous document location", exception);
        }
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
