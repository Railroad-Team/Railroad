package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Immutable snapshot of text document content.
 * <p>
 * Mutable {@link CharSequence} inputs are converted to a {@link String} during
 * construction. The snapshot therefore never observes later caller mutations and is
 * safe to share across analysis threads.
 */
public final class TextDocumentSnapshot implements DocumentSnapshot {
    private final DocumentId id;
    private final DocumentUri uri;
    private final DocumentVersion version;
    private final String languageId;
    private final String text;
    private final Charset encoding;

    /**
     * Creates an immutable text document revision.
     *
     * @param id stable logical document identity
     * @param uri physical or virtual document location
     * @param version content revision
     * @param languageId provider-defined non-blank language identity
     * @param text complete document text; mutable inputs are copied
     * @param encoding character encoding associated with the text origin
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code languageId} is blank
     */
    public TextDocumentSnapshot(
        DocumentId id,
        DocumentUri uri,
        DocumentVersion version,
        String languageId,
        CharSequence text,
        Charset encoding
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.version = Objects.requireNonNull(version, "version");
        this.languageId = requireLanguageId(languageId);
        this.text = Objects.requireNonNull(text, "text").toString();
        this.encoding = Objects.requireNonNull(encoding, "encoding");
    }

    @Override
    public DocumentId id() {
        return id;
    }

    @Override
    public DocumentUri uri() {
        return uri;
    }

    @Override
    public DocumentVersion version() {
        return version;
    }

    @Override
    public String languageId() {
        return languageId;
    }

    /**
     * Returns the immutable complete document text.
     *
     * @return document text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the character encoding associated with the document origin.
     *
     * @return text encoding
     */
    public Charset encoding() {
        return encoding;
    }

    private static String requireLanguageId(String languageId) {
        languageId = Objects.requireNonNull(languageId, "languageId");
        if (languageId.isBlank())
            throw new IllegalArgumentException("languageId cannot be blank");
        return languageId;
    }
}
