package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class LocationTest {

    @Test
    public void textLocationRejectsOutOfBoundsRanges() {
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
    public void textLocationAcceptsEmptyRangesAtEof() {
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
    public void textLocationRejectsNullSnapshot() {
        assertThrows(NullPointerException.class,
            () -> Location.TextLocation.from(null, 0, 0));
    }

    @Test
    public void textLocationValidatesMatchingRevisionIncludingEof() {
        var snapshot = snapshot("Hello\nWorld");
        Location<?> location = Location.TextLocation.from(snapshot, 6, 11);
        var sameRevision = new TextDocumentSnapshot(snapshot.id(),
            DocumentUri.virtual("memory", "renamed.java"), new DocumentVersion(snapshot.version().value()),
            "java", snapshot.text(), StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> location.validateAgainst(snapshot));
        assertDoesNotThrow(() -> location.validateAgainst(sameRevision));
        assertDoesNotThrow(() -> Location.TextLocation.from(snapshot, 11, 11).validateAgainst(snapshot));

        var empty = snapshot("");
        assertDoesNotThrow(() -> Location.TextLocation.from(empty, 0, 0).validateAgainst(empty));
    }

    @Test
    public void textLocationRejectsDifferentDocumentAndVersion() {
        var snapshot = snapshot("Hello\nWorld");
        Location<?> location = Location.TextLocation.from(snapshot, 6, 11);
        var otherDocument = new TextDocumentSnapshot(DocumentId.create(), snapshot.uri(), snapshot.version(),
            "java", snapshot.text(), StandardCharsets.UTF_8);
        var newer = new TextDocumentSnapshot(snapshot.id(), snapshot.uri(), snapshot.version().next(),
            "java", snapshot.text(), StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> location.validateAgainst(otherDocument));
        assertThrows(IllegalArgumentException.class, () -> location.validateAgainst(newer));
        assertThrows(IllegalArgumentException.class,
            () -> Location.TextLocation.from(newer, 6, 11).validateAgainst(snapshot));
    }

    @Test
    public void textLocationRejectsNullAndBinarySnapshots() {
        var snapshot = snapshot("Hello");
        Location<?> location = Location.TextLocation.from(snapshot, 0, 5);
        var binary = binarySnapshot(snapshot.id(), snapshot.version(), 5);

        assertThrows(NullPointerException.class, () -> location.validateAgainst(null));
        assertThrows(IllegalArgumentException.class, () -> location.validateAgainst(binary));
    }

    @Test
    public void textLocationRejectsInconsistentStoredCoordinates() {
        var snapshot = snapshot("Hello\nWorld");
        var range = new TextRange(6, 11);
        var wrongLine = new Location.TextLocation(snapshot.id(), snapshot.version(), range, 0, 0);
        var wrongColumn = new Location.TextLocation(snapshot.id(), snapshot.version(), range, 1, 1);

        assertThrows(IllegalArgumentException.class, () -> wrongLine.validateAgainst(snapshot));
        assertThrows(IllegalArgumentException.class, () -> wrongColumn.validateAgainst(snapshot));
    }

    @Test
    public void textLocationValidationChecksBothRangeBounds() {
        var snapshot = snapshot("Hello\nWorld");
        var invalidStart = new Location.TextLocation(snapshot.id(), snapshot.version(),
            new TextRange(12, 12), 1, 6);
        var invalidEnd = new Location.TextLocation(snapshot.id(), snapshot.version(),
            new TextRange(6, 12), 1, 0);

        assertThrows(IndexOutOfBoundsException.class, () -> invalidStart.validateAgainst(snapshot));
        assertThrows(IndexOutOfBoundsException.class, () -> invalidEnd.validateAgainst(snapshot));
    }

    @Test
    public void binaryLocationValidatesMatchingRevisionIncludingEof() {
        var snapshot = binarySnapshot(DocumentId.create(), DocumentVersion.initial(), 4);
        Location<?> location = Location.BinaryLocation.from(snapshot, 1, 4);
        var sameRevision = new BinaryDocumentSnapshot(snapshot.id(),
            DocumentUri.virtual("memory", "renamed.bin"), new DocumentVersion(snapshot.version().value()),
            "binary", snapshot.copyBytes());

        assertDoesNotThrow(() -> location.validateAgainst(snapshot));
        assertDoesNotThrow(() -> location.validateAgainst(sameRevision));
        assertDoesNotThrow(() -> Location.BinaryLocation.from(snapshot, 4, 4).validateAgainst(snapshot));

        var empty = binarySnapshot(DocumentId.create(), DocumentVersion.initial(), 0);
        assertDoesNotThrow(() -> Location.BinaryLocation.from(empty, 0, 0).validateAgainst(empty));
    }

    @Test
    public void binaryLocationRejectsDifferentDocumentAndVersion() {
        var snapshot = binarySnapshot(DocumentId.create(), DocumentVersion.initial(), 4);
        Location<?> location = Location.BinaryLocation.from(snapshot, 1, 4);
        var otherDocument = new BinaryDocumentSnapshot(DocumentId.create(), snapshot.uri(), snapshot.version(),
            "binary", snapshot.copyBytes());
        var newer = binarySnapshot(snapshot.id(), snapshot.version().next(), 4);

        assertThrows(IllegalArgumentException.class, () -> location.validateAgainst(otherDocument));
        assertThrows(IllegalArgumentException.class, () -> location.validateAgainst(newer));
        assertThrows(IllegalArgumentException.class,
            () -> Location.BinaryLocation.from(newer, 1, 4).validateAgainst(snapshot));
    }

    @Test
    public void binaryLocationRejectsNullAndTextSnapshots() {
        var text = snapshot("Hello");
        var snapshot = binarySnapshot(text.id(), text.version(), 5);
        Location<?> location = Location.BinaryLocation.from(snapshot, 0, 5);

        assertThrows(NullPointerException.class, () -> location.validateAgainst(null));
        assertThrows(IllegalArgumentException.class, () -> location.validateAgainst(text));
    }

    @Test
    public void binaryLocationValidationRejectsRangesBeyondSnapshotSize() {
        var snapshot = binarySnapshot(DocumentId.create(), DocumentVersion.initial(), 4);
        var invalidEnd = new Location.BinaryLocation(snapshot.id(), snapshot.version(), new BinaryRange(1, 5));
        var invalidStart = new Location.BinaryLocation(snapshot.id(), snapshot.version(), new BinaryRange(5, 5));

        assertThrows(IndexOutOfBoundsException.class, () -> invalidEnd.validateAgainst(snapshot));
        assertThrows(IndexOutOfBoundsException.class, () -> invalidStart.validateAgainst(snapshot));
    }

    @Test
    public void binaryLocationFactoryValidatesRangeAndRetainsRevision() {
        var snapshot = binarySnapshot(DocumentId.create(), DocumentVersion.initial(), 4);

        assertThrows(NullPointerException.class, () -> Location.BinaryLocation.from(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Location.BinaryLocation.from(snapshot, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> Location.BinaryLocation.from(snapshot, 3, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> Location.BinaryLocation.from(snapshot, 0, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> Location.BinaryLocation.from(snapshot, 5, 5));

        var location = Location.BinaryLocation.from(snapshot, 1, 4);
        assertEquals(snapshot.id(), location.documentId());
        assertEquals(snapshot.version(), location.documentVersion());
        assertEquals(1, location.range().start);
        assertEquals(4, location.range().end);
        assertDoesNotThrow(() -> location.validateAgainst(snapshot));
    }

    private static BinaryDocumentSnapshot binarySnapshot(DocumentId id, DocumentVersion version, int size) {
        return new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), version, "binary", new byte[size]);
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
