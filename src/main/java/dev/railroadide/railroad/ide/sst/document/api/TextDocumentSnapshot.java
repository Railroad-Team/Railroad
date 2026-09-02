package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;

import dev.railroadide.railroad.ide.language.LanguageSupport;

/**
 * Immutable snapshot of text document content.
 * <p>
 * Mutable {@link CharSequence} inputs are converted to a {@link String} during
 * construction. The snapshot therefore never observes later caller mutations and is
 * safe to share across analysis threads.
 */
public final class TextDocumentSnapshot extends DocumentSnapshot {
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
        Charset encoding) {
        super(id, uri, version, languageId);
        this.text = Objects.requireNonNull(text, "text").toString();
        this.encoding = Objects.requireNonNull(encoding, "encoding");
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

    /**
     * Safely returns the content of a {@link DocumentSnapshot}, only if it is an
     * instance of {@link TextDocumentSnapshot} and is supported by the provided {@link LanguageSupport}.
     *
     * @param snapshot Snapshot to unwrap
     * @param language Required language
     * @return content of the snapshot
     */
    public static Optional<String> unwrap(DocumentSnapshot snapshot, LanguageSupport language) {
        if (snapshot.languageId() != language.languageId())
            return Optional.empty();

        if (snapshot instanceof TextDocumentSnapshot textDocumentSnapshot)
            return Optional.of(textDocumentSnapshot.text());

        return Optional.empty();
    }
}
