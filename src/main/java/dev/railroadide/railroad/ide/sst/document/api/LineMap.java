package dev.railroadide.railroad.ide.sst.document.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, thread-safe mapping between text offsets and line/column positions.
 * Offsets, line indices, and columns are zero-based. Offsets and columns count
 * UTF-16 code units, as in {@link String#length()}, rather than Unicode code points
 * or visual widths. A surrogate pair occupies two columns; combining characters
 * and tabs contribute their own code units.
 * <p>
 * LF, CR, and CRLF are recognized as line terminators, with CRLF treated as one
 * terminator. An empty document has one empty line, and a trailing terminator adds
 * a final empty line. {@link #lineEnd(int)} excludes terminators from line content.
 * Terminator offsets belong to the preceding line: for {@code "Hello\r\n"},
 * offsets 5 and 6 map to columns 5 and 6 of line 0, and offset 7 maps to line 1,
 * column 0. Both conversion methods preserve these positions exactly.
 * <p>
 * Construction takes O(m) time for m UTF-16 code units and O(n) space for n lines.
 * {@link #positionAt(int)} takes O(log n) time; all other queries take O(1) time.
 * Obtain a snapshot's cached map through {@link TextDocumentSnapshot#lineMap()}.
 */
public final class LineMap {
    private final int textLength;
    private final int[] lineStarts;
    private final int[] lineEnds;

    /**
     * Builds a line map for the supplied text without retaining the text itself.
     *
     * @param text complete document text
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public LineMap(String text) {
        Objects.requireNonNull(text, "text");

        List<Integer> lineStartList = new ArrayList<>();
        List<Integer> lineEndList = new ArrayList<>();

        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '\n') {
                lineStartList.add(lineStart);
                lineEndList.add(index);
                lineStart = index + 1;
            } else if (c == '\r') {
                lineStartList.add(lineStart);
                lineEndList.add(index);
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++; // Skip the next character if it's part of a CRLF sequence
                }
                lineStart = index + 1;
            }
        }

        lineStartList.add(lineStart);
        lineEndList.add(text.length());

        this.textLength = text.length();
        this.lineStarts = lineStartList.stream().mapToInt(Integer::intValue).toArray();
        this.lineEnds = lineEndList.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Returns the number of lines, including empty lines.
     *
     * @return line count, always at least one
     */
    public int lineCount() {
        return lineStarts.length;
    }

    /**
     * Returns the offset at which the specified line begins.
     *
     * @param line zero-based line index
     * @return zero-based UTF-16 start offset
     * @throws IndexOutOfBoundsException if {@code line} is negative or at least {@link #lineCount()}
     */
    public int lineStart(int line) {
        return lineStarts[line];
    }

    /**
     * Returns the exclusive end offset of the specified line's content, excluding
     * its line terminator. For CRLF, this is the offset of the CR code unit.
     *
     * @param line zero-based line index
     * @return exclusive zero-based UTF-16 end offset, equal to the start for an empty line
     * @throws IndexOutOfBoundsException if {@code line} is negative or at least {@link #lineCount()}
     */
    public int lineEnd(int line) {
        return lineEnds[line];
    }

    /**
     * Converts an offset to its line and UTF-16 column.
     * Line terminator code units belong to the preceding line, including both
     * code units of CRLF. The offset immediately after a terminator belongs to
     * the next line at column zero. The end-of-document offset is valid and maps
     * to the end of the last line, which may be empty. Offsets between the code
     * units of a surrogate pair are also valid.
     *
     * @param offset zero-based UTF-16 offset, from zero through the text length inclusive
     * @return corresponding zero-based line and column
     * @throws IndexOutOfBoundsException if {@code offset} is negative or exceeds the text length
     */
    public TextPosition positionAt(int offset) {
        if (offset < 0 || offset > textLength)
            throw new IndexOutOfBoundsException("Offset is out of bounds: " + offset);

        int result = Arrays.binarySearch(lineStarts, offset);
        int line = result >= 0 ? result : -result - 2;
        int column = offset - lineStarts[line];

        return new TextPosition(line, column);
    }

    /**
     * Converts a line and UTF-16 column to an offset.
     * Columns may address line terminator code units, including the LF of CRLF.
     * For every line except the last, the largest valid column addresses the code
     * unit immediately before the next line starts. The last line permits its
     * end-of-document position. An offset at the next line's start must be
     * represented as column zero of that next line.
     * <p>
     * For every valid offset, {@code offsetAt(positionAt(offset))} returns that offset.
     *
     * @param position zero-based line and UTF-16 column
     * @return corresponding zero-based UTF-16 offset
     * @throws NullPointerException if {@code position} is {@code null}
     * @throws IndexOutOfBoundsException if the line does not exist or the column exceeds
     *             the valid range for that line
     */
    public int offsetAt(TextPosition position) {
        int line = position.line();
        int column = position.column();

        if (line < 0 || line >= lineCount())
            throw new IndexOutOfBoundsException("Line is out of bounds: " + line);

        int maxOffset = line == lineCount() - 1
            ? textLength
            : lineStarts[line + 1] - 1;

        int maxColumn = maxOffset - lineStarts[line];
        if (column < 0 || column > maxColumn)
            throw new IndexOutOfBoundsException("Column is out of bounds: " + column);

        return lineStart(line) + column;
    }
}
