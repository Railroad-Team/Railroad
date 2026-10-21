package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RelatedLocationTest {

    @Test
    public void retainsTextAndBinaryLocationsAndMessages() {
        var id = DocumentId.create();
        var version = DocumentVersion.initial();
        Location<?>[] locations = {
            new Location.TextLocation(id, version, new TextRange(0, 3), 0, 0),
            new Location.BinaryLocation(id, version, new BinaryRange(0, 3))
        };

        for (Location<?> location : locations) {
            var related = new RelatedLocation(location, "Previously declared here");

            assertSame(location, related.location());
            assertEquals("Previously declared here", related.message());
        }
    }

    @Test
    public void rejectsNullLocation() {
        assertThrows(IllegalArgumentException.class,
            () -> new RelatedLocation(null, "Previously declared here"));
    }

    @Test
    public void rejectsNullOrEmptyMessage() {
        var location = new Location.TextLocation(DocumentId.create(), DocumentVersion.initial(),
            new TextRange(0, 0), 0, 0);

        assertThrows(IllegalArgumentException.class, () -> new RelatedLocation(location, null));
        assertThrows(IllegalArgumentException.class, () -> new RelatedLocation(location, ""));
    }
}
