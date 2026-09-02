package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;

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
public abstract sealed class DocumentSnapshot permits TextDocumentSnapshot, BinaryDocumentSnapshot {

    private final DocumentId id;
    private final DocumentUri uri;
    private final DocumentVersion version;
    private final String languageId;

    /**
     * Returns the stable identity shared by every revision of this logical document.
     *
     * @return logical document identity
     */
    public DocumentId id() { return id; }

    /**
     * Returns the physical or virtual location captured by this snapshot.
     *
     * @return document URI
     */
    public DocumentUri uri() { return uri; }

    /**
     * Returns the immutable content revision captured by this snapshot.
     *
     * @return document version
     */
    public DocumentVersion version() { return version; }

    /**
     * Returns the provider-defined language identity for this content.
     *
     * @return non-blank language ID
     */
    public String languageId() { return languageId; }

    protected DocumentSnapshot(
        DocumentId id,
        DocumentUri uri,
        DocumentVersion version,
        String languageId)
    {
        this.id = Objects.requireNonNull(id, "id");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.version = Objects.requireNonNull(version, "version");
        this.languageId = requireLanguageId(languageId, uri);
    }

    private static String requireLanguageId(String languageId, DocumentUri uri)
    {
        languageId = Objects.requireNonNull(languageId, "languageId");
        if (languageId.isBlank())
            throw new IllegalArgumentException("languageId cannot be blank");

        LanguageSupportRegistry.getExpected(languageId);

        return languageId;
    }
}
