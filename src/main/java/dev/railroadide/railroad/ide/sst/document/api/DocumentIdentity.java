package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * Stable logical identity and current address of a document.
 * <p>
 * The ID survives aliases, moves, and renames. The URI describes the document's current
 * physical or virtual location and may change while the ID remains stable.
 *
 * @param id stable logical document ID
 * @param uri current physical or virtual document URI
 */
public record DocumentIdentity(DocumentId id, DocumentUri uri) {
    public DocumentIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uri, "uri");
    }

    /**
     * Returns this logical identity at a new current address.
     *
     * @param newUri new physical or virtual address
     * @return updated identity value retaining the same stable ID
     */
    public DocumentIdentity at(DocumentUri newUri) {
        return new DocumentIdentity(id, newUri);
    }
}
