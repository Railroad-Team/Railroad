package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RangeEqualityTest {
    @Test
    public void rangesCompareByKindAndBounds() {
        assertEquals(new TextRange(1, 4), new TextRange(1, 4));
        assertEquals(new TextRange(1, 4).hashCode(), new TextRange(1, 4).hashCode());
        assertNotEquals(new TextRange(1, 4), new TextRange(1, 5));
        assertNotEquals(new TextRange(1, 4), new BinaryRange(1, 4));
        assertEquals("TextRange[1, 4)", new TextRange(1, 4).toString());
    }

    @Test
    public void locationsWithEqualRangesAreEqualValues() {
        var id = DocumentId.create();
        var first = new Location.TextLocation(id, DocumentVersion.initial(), new TextRange(2, 3), 0, 2);
        var second = new Location.TextLocation(id, DocumentVersion.initial(), new TextRange(2, 3), 0, 2);
        assertEquals(1, Set.of(first).stream().filter(second::equals).count());
        assertNotEquals(first, new Location.TextLocation(id, DocumentVersion.initial().next(), new TextRange(2, 3),
            0, 2));
    }
}
