package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocationTest {

    @Test
    void textLocationRejectsOutOfBoundsRanges() {
        var snapshot = snapshot("Hello\nWorld");

        assertThrows(IndexOutOfBoundsException.class,
            () -> Location.TextLocation.from(snapshot, 0, 100));
        assertThrows(IndexOutOfBoundsException.class,
            () -> Location.TextLocation.from(snapshot, 12, 12));
        assertThrows(IllegalArgumentException.class,
            () -> Location.TextLocation.from(snapshot, -1, 3));
        assertThrows(IllegalArgumentException.class,
            () -> Location.TextLocation.from(snapshot, 5, 3));

        var location = Location.TextLocation.from(snapshot, 6, 11);

        assertEquals(snapshot.id(), location.documentId());
        assertEquals(snapshot.version(), location.documentVersion());
        assertEquals(6, location.range().start);
        assertEquals(11, location.range().end);
        assertEquals(1, location.line());
        assertEquals(0, location.column());
    }

    @Test
    void textLocationAcceptsEmptyRangesAtEof() {
        var location = Location.TextLocation.from(snapshot("Hello\nWorld"), 11, 11);
        assertEquals(1, location.line());
        assertEquals(5, location.column());
        assertEquals(0, location.range().length());

        var empty = Location.TextLocation.from(snapshot(""), 0, 0);
        assertEquals(0, empty.line());
        assertEquals(0, empty.column());

        var trailingNewline = Location.TextLocation.from(snapshot("Hello\n"), 6, 6);
        assertEquals(1, trailingNewline.line());
        assertEquals(0, trailingNewline.column());
    }

    @Test
    void textLocationRejectsNullSnapshot() {
        assertThrows(NullPointerException.class,
            () -> Location.TextLocation.from(null, 0, 0));
    }

    private static TextDocumentSnapshot snapshot(String text) {
        DocumentId id = DocumentId.create();
        return new TextDocumentSnapshot(
            id,
            DocumentUri.inMemory(id),
            DocumentVersion.initial(),
            "java",
            text,
            StandardCharsets.UTF_8);
    }
}
