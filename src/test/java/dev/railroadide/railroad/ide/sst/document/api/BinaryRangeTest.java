package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinaryRangeTest {
    @Test
    public void binaryRangesUseByteOffsets() {
        byte[] bytes = {10, 20, 30, 40};
        var snapshot = snapshot(bytes);
        var range = new BinaryRange(1, 3);

        assertEquals(2, range.length());
        assertTrue(range.contains(1));
        assertTrue(range.contains(2));
        assertFalse(range.contains(3));

        ByteBuffer content = range.content(snapshot);

        assertEquals(2, content.remaining());
        assertEquals(20, content.get());
        assertEquals(30, content.get());
        assertTrue(content.isReadOnly());
    }

    @Test
    public void emptyBinaryRangesReturnEmptyContent() {
        var snapshot = snapshot(new byte[]{10, 20});
        var range = new BinaryRange(1, 1);

        assertEquals(0, range.length());
        assertEquals(0, range.content(snapshot).remaining());
    }

    @Test
    public void binaryRangeContentRejectsOutOfBoundsRanges() {
        var snapshot = snapshot(new byte[]{10, 20});

        assertThrows(IndexOutOfBoundsException.class, () -> new BinaryRange(1, 3).content(snapshot));
    }

    private static BinaryDocumentSnapshot snapshot(byte[] bytes) {
        var id = DocumentId.create();
        return new BinaryDocumentSnapshot(
            id,
            DocumentUri.inMemory(id),
            DocumentVersion.initial(),
            "binary",
            bytes);
    }
}
