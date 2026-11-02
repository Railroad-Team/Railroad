package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Optional;

public interface DocumentStore {
    void publishBacking(DocumentSnapshot snapshot);

    void publishOverlay(DocumentSnapshot snapshot);

    Optional<DocumentSnapshot> current(DocumentId id);

    /**
     * Returns an atomic view of the document's backing, overlay, and overlay base revision.
     * The returned state is immutable and is not affected by later publications.
     *
     * @param id logical document identity
     * @return current state, or empty if the document has no published snapshots
     */
    Optional<DocumentState> state(DocumentId id);

    void reconcileOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion,
        DocumentSnapshot resolvedSnapshot
    );

    /**
     * Promotes the current overlay to backing after a successful save, retaining its
     * snapshot and version and clearing the overlay. Validation and replacement are
     * atomic. This operation performs no filesystem I/O or filesystem conflict detection.
     * The caller must protect against external filesystem changes during persistence.
     *
     * @param id logical document identity
     * @param expectedBackingVersion backing revision observed by the save operation
     * @param expectedOverlayVersion overlay revision saved by the save operation
     * @throws NullPointerException if either expected version is {@code null}
     * @throws IllegalStateException if backing or overlay is absent, either expected
     *             version differs, the overlay is stale, or its version is not strictly newer than backing
     */
    void commitOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion
    );

    /**
     * Discards unsaved changes by replacing backing with a newer revision of its
     * existing content and metadata and clearing the overlay. Validation and replacement are atomic.
     * This operation performs no filesystem I/O.
     *
     * @param id logical document identity
     * @param expectedBackingVersion backing revision observed when discard was requested
     * @param expectedOverlayVersion overlay revision observed when discard was requested
     * @param restoredBacking snapshot with the backing's document ID and text or binary
     *            content, URI, language, origin, and encoding where applicable, and a
     *            version strictly newer than both published snapshots
     * @throws NullPointerException if either expected version or restored backing is {@code null}
     * @throws IllegalStateException if backing or overlay is absent, or either expected version differs
     * @throws IllegalArgumentException if restored backing has a different document ID or
     *             content or metadata, or its version is not strictly newer than both published snapshots
     */
    void discardOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion,
        DocumentSnapshot restoredBacking
    );
}
