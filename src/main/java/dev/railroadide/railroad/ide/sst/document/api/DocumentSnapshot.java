package dev.railroadide.railroad.ide.sst.document.api;

/**
 * Immutable view of one version of a physical or virtual document.
 * <p>
 * A snapshot permanently binds logical identity, current location, content revision,
 * language identity, and either text or binary content. Owners create a new snapshot
 * when content changes; consumers may safely retain and share older snapshots across
 * worker threads.
 * <p>
 * Snapshot equality semantics are intentionally not defined by this root contract.
 */
public sealed interface DocumentSnapshot permits TextDocumentSnapshot, BinaryDocumentSnapshot {
    /**
     * Returns the stable identity shared by every revision of this logical document.
     *
     * @return logical document identity
     */
    DocumentId id();

    /**
     * Returns the physical or virtual location captured by this snapshot.
     *
     * @return document URI
     */
    DocumentUri uri();

    /**
     * Returns the immutable content revision captured by this snapshot.
     *
     * @return document version
     */
    DocumentVersion version();

    /**
     * Returns the provider-defined language identity for this content.
     *
     * @return non-blank language ID
     */
    String languageId();
}
