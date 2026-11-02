package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Optional;

/**
 * Project-owned store of immutable backing documents, editor overlays, and workspace
 * configuration. The current document is its overlay whenever one exists, including
 * a stale overlay; otherwise it is the backing snapshot.
 * <p>
 * Each operation is atomic with respect to other store operations. Successful state
 * changes advance the workspace revision once; rejected operations and documented
 * no-ops preserve all published state and the workspace revision. A sequence of
 * separate reads may observe different revisions; use {@link #capture()} for a
 * consistent view of all current documents and the configuration.
 * <p>
 * The owner supplies content versions, configuration revisions, and snapshots. Store
 * operations perform no filesystem I/O. Persistence and external-change detection
 * belong to the owner; {@link #commitOverlay(DocumentId, DocumentVersion, DocumentVersion)}
 * represents the in-memory transition after persistence succeeds. Previously returned
 * snapshots remain readable after publication, removal, or overlay lifecycle changes.
 */
public interface DocumentStore {
    /**
     * Publishes backing content. Reusing its current version requires identical text
     * or binary content, while metadata may change. Republishing the same snapshot
     * instance is a no-op.
     * After backing removal, its next publication must have a strictly newer version
     * than the highest previously published backing revision for that document ID.
     * An existing overlay and its base revision are retained, even if the new backing
     * makes that overlay stale. Backing revisions created by commit and discard are
     * included in the retained version history.
     *
     * @param snapshot immutable backing snapshot
     * @throws NullPointerException if the snapshot is {@code null}
     * @throws IllegalArgumentException if the version regresses, or content differs
     *             at the current backing version, or reappearing backing does not
     *             advance beyond its last published backing revision
     */
    void publishBacking(DocumentSnapshot snapshot);

    /**
     * Removes published backing after validating its expected version. An existing
     * overlay and its base revision are retained; otherwise the document is removed.
     * The highest published backing revision is retained to validate reappearance.
     * Validation and removal are atomic and perform no filesystem I/O.
     *
     * @param id logical document identity
     * @param expectedBackingVersion backing revision observed when removal was requested
     * @throws NullPointerException if the expected backing version is {@code null}
     * @throws IllegalStateException if the document or backing is absent, or the
     *             backing version differs from the expected version
     */
    void removeBacking(DocumentId id, DocumentVersion expectedBackingVersion);

    /**
     * Publishes editor content, preserving an existing overlay's base backing revision.
     * A first overlay over existing backing must have a later version, or the same
     * version and identical text or binary content. An existing overlay may remain
     * older than backing as long as its own version does not decrease.
     * Reusing the current overlay version requires identical content, while metadata
     * may change. Republishing the same snapshot instance is a no-op.
     * A first overlay captures the current backing version as its base, or an absent
     * base when no backing exists. Later overlay publications preserve that base,
     * including while backing is absent or has advanced beyond the overlay version.
     *
     * @param snapshot immutable overlay snapshot
     * @throws NullPointerException if the snapshot is {@code null}
     * @throws IllegalArgumentException if its version regresses, or content differs
     *             at the current overlay version or a first overlay's backing version
     */
    void publishOverlay(DocumentSnapshot snapshot);

    /**
     * Returns the document's current published snapshot. An overlay takes precedence
     * over backing regardless of their relative versions or the overlay's staleness.
     *
     * @param id logical document identity
     * @return exact overlay instance if present, otherwise backing, or empty if neither exists
     */
    Optional<DocumentSnapshot> current(DocumentId id);

    /**
     * Returns an atomic view of the document's backing, overlay, and overlay base revision.
     * The returned state is immutable and is not affected by later publications.
     * Removed backing is represented by an absent backing snapshot when an overlay
     * remains. Retained backing-version history alone does not produce a document state.
     *
     * @param id logical document identity
     * @return current state, or empty if the document has no published snapshots
     */
    Optional<DocumentState> state(DocumentId id);

    /**
     * Replaces an overlay with caller-resolved content based on the current backing.
     * Both published versions must still match those observed when reconciliation
     * was requested. The resolved snapshot must belong to the same document and have
     * a version strictly newer than both the current backing and overlay.
     * <p>
     * On success, the exact resolved snapshot becomes the overlay and its base becomes
     * the current backing version, clearing staleness. The backing snapshot is retained.
     * This operation publishes an already computed resolution; it performs no merging
     * or persistence itself.
     *
     * @param id logical document identity
     * @param expectedBackingVersion backing revision used to compute the resolution
     * @param expectedOverlayVersion overlay revision used to compute the resolution
     * @param resolvedSnapshot immutable resolved overlay snapshot
     * @throws NullPointerException if either expected version or the resolved snapshot is {@code null}
     * @throws IllegalStateException if backing or overlay is absent, or either expected version differs
     * @throws IllegalArgumentException if the resolved document ID differs, or its
     *             version is not strictly newer than both published snapshots
     */
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
     * Closes an unchanged overlay, retaining the exact backing snapshot and its version.
     * Backing and overlay must have matching content and versions, and the overlay
     * must not be stale. Validation and removal are atomic and perform no filesystem I/O.
     *
     * @param id logical document identity
     * @param expectedBackingVersion backing revision observed when removal was requested
     * @param expectedOverlayVersion overlay revision observed when removal was requested
     * @throws NullPointerException if either expected version is {@code null}
     * @throws IllegalStateException if backing or overlay is absent, either expected
     *             version differs, the overlay is stale, or its version or content differs from backing
     */
    void removeUnchangedOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion
    );

    /**
     * Discards unsaved changes by replacing backing with a newer revision of its
     * existing content and metadata and clearing the overlay. Validation and replacement are atomic.
     * This operation performs no filesystem I/O.
     * A stale overlay may be discarded: the restored content must match the latest
     * backing, which may differ from the backing on which the overlay was based.
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
     * The snapshot retains the owning store's project identity and published configuration.
     * Its initial configuration is supplied at store construction, independently of
     * the initial workspace revision of zero.
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

    /**
     * Publishes an immutable configuration, advancing the workspace revision once.
     * Configuration revisions are independent of workspace revisions. Republishing
     * the same configuration revision and module IDs is a no-op.
     * A newer configuration revision advances the workspace revision once even if
     * its module IDs are unchanged. Existing workspace captures retain their previous
     * configuration snapshot.
     *
     * @param configuration configuration to retain in subsequent captures
     * @throws NullPointerException if the configuration is {@code null}
     * @throws IllegalArgumentException if the configuration revision is older than
     *             the current configuration, or equal with different module IDs
     */
    void publishConfiguration(WorkspaceConfigurationSnapshot configuration);
}
