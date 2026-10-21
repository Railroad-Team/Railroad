package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LineMapTest {
    @Test
    public void handlesLineEndings() {
        var map = new LineMap("Hello\r\nWorld\n");

        assertEquals(3, map.lineCount());

        assertEquals(0, map.lineStart(0));
        assertEquals(5, map.lineEnd(0));

        assertEquals(7, map.lineStart(1));
        assertEquals(12, map.lineEnd(1));

        assertEquals(13, map.lineStart(2));
        assertEquals(13, map.lineEnd(2));
    }

    @Test
    public void convertsOffsetsToPositions() {
        var map = new LineMap("Hello\r\nWorld\n");

        assertEquals(new TextPosition(0, 0), map.positionAt(0));
        assertEquals(new TextPosition(0, 3), map.positionAt(3));

        // Inside the CRLF sequence
        assertEquals(new TextPosition(0, 5), map.positionAt(5));
        assertEquals(new TextPosition(0, 6), map.positionAt(6));

        // Second line
        assertEquals(new TextPosition(1, 0), map.positionAt(7));
        assertEquals(new TextPosition(1, 2), map.positionAt(9));

        // Final empty line
        assertEquals(new TextPosition(2, 0), map.positionAt(13));
    }

    @Test
    public void offsetsAndPositionsRoundTrip() {
        String text = "Hello\r\nWorld\nGoodbye\rTest\n\n";
        var map = new LineMap(text);

        for (int offset = 0; offset <= text.length(); offset++) {
            TextPosition position = map.positionAt(offset);

            assertEquals(
                offset,
                map.offsetAt(position),
                "Round trip failed at offset " + offset);
        }
    }

    @Test
    public void convertsPositionsToOffsets() {
        var map = new LineMap("Hello\r\nWorld\n");

        assertEquals(0, map.offsetAt(new TextPosition(0, 0)));
        assertEquals(3, map.offsetAt(new TextPosition(0, 3)));
        assertEquals(5, map.offsetAt(new TextPosition(0, 5)));
        assertEquals(6, map.offsetAt(new TextPosition(0, 6)));
        assertEquals(7, map.offsetAt(new TextPosition(1, 0)));
        assertEquals(9, map.offsetAt(new TextPosition(1, 2)));
        assertEquals(12, map.offsetAt(new TextPosition(1, 5)));
        assertEquals(13, map.offsetAt(new TextPosition(2, 0)));
    }

    @Test
    public void convertsPositionsInEmptyDocumentsAndEmptyLines() {
        var empty = new LineMap("");
        assertEquals(0, empty.offsetAt(new TextPosition(0, 0)));
        assertThrows(IndexOutOfBoundsException.class, () -> empty.offsetAt(new TextPosition(0, 1)));

        var map = new LineMap("\n\r\n");
        assertEquals(0, map.offsetAt(new TextPosition(0, 0)));
        assertEquals(1, map.offsetAt(new TextPosition(1, 0)));
        assertEquals(3, map.offsetAt(new TextPosition(2, 0)));
        assertEquals(2, map.offsetAt(new TextPosition(1, 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(1, 2)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(2, 1)));
    }

    @Test
    public void convertsPositionsAcrossMixedTerminators() {
        var map = new LineMap("a\rb\nc\r\nd");

        assertEquals(1, map.offsetAt(new TextPosition(0, 1)));
        assertEquals(2, map.offsetAt(new TextPosition(1, 0)));
        assertEquals(3, map.offsetAt(new TextPosition(1, 1)));
        assertEquals(4, map.offsetAt(new TextPosition(2, 0)));
        assertEquals(5, map.offsetAt(new TextPosition(2, 1)));
        assertEquals(7, map.offsetAt(new TextPosition(3, 0)));
        assertEquals(8, map.offsetAt(new TextPosition(3, 1)));
    }

    @Test
    public void convertsUtf16ColumnsToOffsets() {
        var map = new LineMap("\uD83D\uDE00\n\uD83D\uDE80!");

        assertEquals(1, map.offsetAt(new TextPosition(0, 1)));
        assertEquals(2, map.offsetAt(new TextPosition(0, 2)));
        assertEquals(3, map.offsetAt(new TextPosition(1, 0)));
        assertEquals(5, map.offsetAt(new TextPosition(1, 2)));
        assertEquals(6, map.offsetAt(new TextPosition(1, 3)));
    }

    @Test
    public void countsBothSurrogateCodeUnitsInPositions() {
        var map = new LineMap("A\uD83D\uDE00B\n");

        assertEquals(0, map.lineStart(0));
        assertEquals(4, map.lineEnd(0));
        assertEquals(5, map.lineStart(1));
        for (int offset = 0; offset <= 4; offset++) {
            var expected = new TextPosition(0, offset);
            assertEquals(expected, map.positionAt(offset), "Position at offset " + offset);
            assertEquals(offset, map.offsetAt(expected), "Offset at column " + offset);
        }
        assertEquals(new TextPosition(1, 0), map.positionAt(5));
        assertEquals(5, map.offsetAt(new TextPosition(1, 0)));
    }

    @Test
    public void countsCombiningCharactersAndTabsAsCodeUnits() {
        String text = "e\u0301\tHello";
        var map = new LineMap(text);

        assertEquals(1, map.lineCount());
        assertEquals(8, map.lineEnd(0));
        for (int offset = 0; offset <= 8; offset++) {
            var expected = new TextPosition(0, offset);
            assertEquals(expected, map.positionAt(offset), "Position at offset " + offset);
            assertEquals(offset, map.offsetAt(expected), "Offset at column " + offset);
        }
    }

    @Test
    public void rejectsInvalidOffsetsAndPositions() {
        var map = new LineMap("abc\ndef");

        assertThrows(IndexOutOfBoundsException.class, () -> map.positionAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> map.positionAt(8));
        assertThrows(IndexOutOfBoundsException.class, () -> map.lineStart(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> map.lineEnd(2));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(0, 5)));

        assertEquals(new TextPosition(1, 3), map.positionAt(7));
        assertEquals(7, map.offsetAt(new TextPosition(1, 3)));
    }

    @Test
    public void rejectsPositionsBeyondLineOrColumnBounds() {
        var map = new LineMap("Hello\r\nWorld\n");

        // A column cannot reach the next line's start; that offset belongs to the next line.
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(0, 7)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(1, 6)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(2, 1)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(3, 0)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(Integer.MAX_VALUE, 0)));
        assertThrows(IndexOutOfBoundsException.class, () -> map.offsetAt(new TextPosition(0, Integer.MAX_VALUE)));
    }

    @Test
    public void rejectsNullPosition() {
        var map = new LineMap("Hello");

        assertThrows(NullPointerException.class, () -> map.offsetAt(null));
    }

    @Test
    public void handlesEmptyDocument() {
        var map = new LineMap("");

        assertEquals(1, map.lineCount());
        assertEquals(0, map.lineStart(0));
        assertEquals(0, map.lineEnd(0));
    }

    @Test
    public void handlesSingleLineWithoutTerminator() {
        assertLines("Hello world", new int[]{0}, new int[]{11});
    }

    @Test
    public void excludesEachKindOfLineTerminatorFromLineEnds() {
        assertLines("one\ntwo", new int[]{0, 4}, new int[]{3, 7});
        assertLines("one\rtwo", new int[]{0, 4}, new int[]{3, 7});
        assertLines("one\r\ntwo", new int[]{0, 5}, new int[]{3, 8});
    }

    @Test
    public void retainsEmptyFinalLineAfterEachKindOfTerminator() {
        assertLines("one\n", new int[]{0, 4}, new int[]{3, 4});
        assertLines("one\r", new int[]{0, 4}, new int[]{3, 4});
        assertLines("one\r\n", new int[]{0, 5}, new int[]{3, 5});
    }

    @Test
    public void handlesLeadingAndConsecutiveEmptyLines() {
        assertLines("\n\ntext\n\n", new int[]{0, 1, 2, 7, 8}, new int[]{0, 1, 6, 7, 8});
    }

    @Test
    public void handlesDocumentsContainingOnlyTerminators() {
        assertLines("\n", new int[]{0, 1}, new int[]{0, 1});
        assertLines("\r", new int[]{0, 1}, new int[]{0, 1});
        assertLines("\r\n", new int[]{0, 2}, new int[]{0, 2});
        assertLines("\r\r\n\n", new int[]{0, 1, 3, 4}, new int[]{0, 1, 3, 4});
    }

    @Test
    public void handlesMixedTerminatorsWithoutTrailingNewline() {
        assertLines("a\rb\nc\r\nd", new int[]{0, 2, 4, 7}, new int[]{1, 3, 5, 8});
    }

    @Test
    public void usesUtf16OffsetsForSupplementaryCharacters() {
        assertLines("\uD83D\uDE00\n\uD83D\uDE80!", new int[]{0, 3}, new int[]{2, 6});
    }

    @Test
    public void rejectsNullText() {
        assertThrows(NullPointerException.class, () -> new LineMap(null));
    }

    @Test
    public void rejectsLineIndicesOutsideTheMap() {
        var map = new LineMap("first\nsecond");

        assertThrows(IndexOutOfBoundsException.class, () -> map.lineStart(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> map.lineEnd(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> map.lineStart(map.lineCount()));
        assertThrows(IndexOutOfBoundsException.class, () -> map.lineEnd(map.lineCount()));
    }

    private static void assertLines(String text, int[] starts, int[] ends) {
        var map = new LineMap(text);

        assertEquals(starts.length, map.lineCount());
        for (int line = 0; line < starts.length; line++) {
            assertEquals(starts[line], map.lineStart(line), "Start of line " + line);
            assertEquals(ends[line], map.lineEnd(line), "End of line " + line);
        }
    }
}
