package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * Immutable location of a source associated with a document's provenance.
 * A reference does not retain the source content or its revision.
 * It identifies a location rather than a stable {@link DocumentId}, so it does not
 * automatically follow source renames or provide source-range mappings.
 *
 * @param uri physical or virtual source location
 */
public record OriginReference(DocumentUri uri) {
    /**
     * Creates a reference to a source location.
     *
     * @param uri physical or virtual source location
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public OriginReference {
        uri = Objects.requireNonNull(uri, "uri");
    }
}
