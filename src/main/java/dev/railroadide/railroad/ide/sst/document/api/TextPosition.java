package dev.railroadide.railroad.ide.sst.document.api;

/**
 * Immutable, thread-safe position with a zero-based line and UTF-16 column.
 * Columns count UTF-16 code units from the line start, not Unicode code points,
 * grapheme clusters, or visual widths. A surrogate pair occupies two columns;
 * combining characters and tabs contribute their own code units.
 * <p>
 * A position carries no document identity and does not validate document bounds.
 * {@link LineMap#offsetAt(TextPosition)} validates it against a particular map.
 * Positions produced by {@link LineMap#positionAt(int)} may address terminator
 * code units, including either code unit of CRLF, on the preceding line.
 *
 * @param line zero-based, non-negative line index
 * @param column zero-based, non-negative UTF-16 column
 */
public record TextPosition(int line, int column) {
    /**
     * Creates a position without checking it against a document.
     *
     * @param line zero-based, non-negative line index
     * @param column zero-based, non-negative UTF-16 column
     * @throws IllegalArgumentException if {@code line} or {@code column} is negative
     */
    public TextPosition {
        if (line < 0)
            throw new IllegalArgumentException("Line number cannot be negative: " + line);

        if (column < 0)
            throw new IllegalArgumentException("Column number cannot be negative: " + column);
    }
}
