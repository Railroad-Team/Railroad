package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * Immutable view of one version of a physical or virtual document.
 * <p>
 * A snapshot permanently binds logical identity, current location, content revision,
 * language identity, origin, and either text or binary content. Owners create a new snapshot
 * when content changes; consumers may safely retain and share older snapshots across
 * worker threads.
 * <p>
 * Snapshot equality semantics are intentionally not defined by this root contract.
 * <p>
 * Compatibility note: this root was previously an abstract sealed class. Its change
 * to an interface is a binary compatibility break; compiled consumers of the old
 * class must be rebuilt. Retained text/binary constructors provide source compatibility
 * for ordinary callers, but the old superclass constructor is no longer available.
 * Construction also no longer validates registration in the language support registry:
 * consumers requiring language-specific operations resolve support at their boundary.
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
     * Registration is not required; consumers resolve language support when needed.
     *
     * @return non-blank language ID
     */
    String languageId();

    /**
     * Returns the representation of this snapshot's content.
     *
     * @return text or binary content kind
     */
    ContentKind contentKind();

    /**
     * Returns the backing, provenance, and source references for this revision.
     *
     * @return immutable document origin
     */
    DocumentOrigin origin();

    /**
     * Validates a language identity without consulting the language registry.
     *
     * @param languageId provider-defined language identity
     * @return the supplied non-blank identity
     * @throws NullPointerException if {@code languageId} is {@code null}
     * @throws IllegalArgumentException if {@code languageId} is blank
     */
    static String requireLanguageId(String languageId) {
        languageId = Objects.requireNonNull(languageId, "languageId");
        if (languageId.isBlank())
            throw new IllegalArgumentException("languageId cannot be blank");

        return languageId;
    }
}
