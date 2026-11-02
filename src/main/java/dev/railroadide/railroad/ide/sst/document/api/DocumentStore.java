package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Optional;

public interface DocumentStore {
    void publishBacking(DocumentSnapshot snapshot);

    /**
     * Publishes editor content, preserving an existing overlay's base backing revision.
     * A first overlay over existing backing must have a later version, or the same
     * version and identical text or binary content. An existing overlay may remain
     * older than backing as long as its own version does not decrease.
     *
     * @param snapshot immutable overlay snapshot
     * @throws IllegalArgumentException if its version regresses, or a first overlay
     *             has the backing's version with different content
     */
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

    /**
     * Atomically captures the store's published documents, preferring each document's
     * overlay when present. Later publications do not affect the captured state.
     * The workspace revision starts at zero and advances once per successful state
     * change, including backing updates hidden by an overlay. Republishing the exact
     * same snapshot instance to its existing slot is a no-op. Reads and rejected
     * operations do not advance the revision.
     * This operation captures published store state and performs no filesystem I/O;
     * it does not guarantee a simultaneous snapshot of filesystem contents.
     *
     * @return immutable workspace snapshot
     */
    WorkspaceSnapshot capture();
}
