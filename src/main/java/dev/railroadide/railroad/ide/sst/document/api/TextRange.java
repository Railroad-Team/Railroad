package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.CharBuffer;

/**
 * An immutable, half-open range of UTF-16 code unit offsets into a
 * {@link TextDocumentSnapshot}.
 * Offsets use the same units as {@link String#length()} and are independent of
 * the snapshot's character encoding. A supplementary Unicode code point occupies
 * two offsets; range bounds are not required to align with code point boundaries.
 */
public final class TextRange extends Range<TextDocumentSnapshot> {
    /**
     * Creates a text range with the given half-open bounds.
     * Snapshot bounds are checked when {@link #content(TextDocumentSnapshot)} is called.
     *
     * @param start inclusive start offset in UTF-16 code units
     * @param end exclusive end offset in UTF-16 code units
     * @throws IllegalArgumentException if {@code start} is negative or {@code end < start}
     */
    public TextRange(int start, int end) {
        super(start, end);
    }

    /**
     * Returns a read-only view over this range within the snapshot.
     * Each call returns a buffer positioned at zero with its limit and capacity
     * equal to {@link #length()}. An empty range returns an empty buffer, provided
     * its offset is within the snapshot bounds, including the end of the text.
     *
     * @param snapshot snapshot whose text is viewed
     * @return a read-only buffer containing the text between the start and end offsets
     * @throws NullPointerException if {@code snapshot} is {@code null}
     * @throws IndexOutOfBoundsException if this range extends beyond the snapshot's text
     */
    public CharBuffer content(TextDocumentSnapshot snapshot) {
        return CharBuffer.wrap(snapshot.text())
            .slice(start, length())
            .asReadOnlyBuffer();
    }
}
