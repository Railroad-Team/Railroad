package dev.railroadide.railroad.ide.sst.document.api;

// SST-P0-034: Add a language-neutral `Location` containing:
//  document identity, snapshot version, and range.
/**
 * Represents a location within a document.
 * @param <S> The type of {@link DocumentSnapshot} supported
 */
public interface Location<S extends DocumentSnapshot> {

    /** The identity of the current document */
    public DocumentId documentId();

    /** The version of the current document */
    public DocumentVersion documentVersion();

    /** The targeted range of content within the docuement */
    public Range<S> range();
}
