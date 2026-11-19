package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextRangeTest {
    @Test
    public void textRangesUseUtf16Offsets() {
        String text = "A\uD83D\uDE00B";
        var snapshot = snapshot(text);
        var range = new TextRange(1, 3);

        assertEquals(4, snapshot.text().length());
        assertEquals(2, range.length());
        assertTrue(range.contains(1));
        assertTrue(range.contains(2));
        assertFalse(range.contains(3));

        CharBuffer content = range.content(snapshot);

        assertEquals(2, content.remaining());
        assertEquals("\uD83D\uDE00", content.toString());
        assertTrue(content.isReadOnly());
    }

    @Test
    public void emptyTextRangesReturnEmptyContent() {
        var snapshot = snapshot("AB");
        var range = new TextRange(1, 1);

        assertEquals(0, range.length());
        assertEquals("", range.content(snapshot).toString());
    }

    @Test
    public void textRangeContentRejectsOutOfBoundsRanges() {
        var snapshot = snapshot("AB");

        assertThrows(IndexOutOfBoundsException.class, () -> new TextRange(1, 3).content(snapshot));
    }

    private static TextDocumentSnapshot snapshot(String text) {
        var id = DocumentId.create();
        return new TextDocumentSnapshot(
            id,
            DocumentUri.inMemory(id),
            DocumentVersion.initial(),
            "java",
            text,
            StandardCharsets.UTF_8);
    }
}
