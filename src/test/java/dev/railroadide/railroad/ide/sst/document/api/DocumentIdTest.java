package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentIdTest {
    @Test
    public void freshlyAllocatedIdsAreDistinct() {
        assertNotEquals(DocumentId.create(), DocumentId.create());
    }

    @Test
    public void externalFormRoundTrips() {
        var original = new DocumentId(UUID.fromString("12345678-1234-5678-9abc-def012345678"));

        assertEquals("12345678-1234-5678-9abc-def012345678", original.toString());
        assertEquals(original, DocumentId.parse(original.toString()));
        assertEquals(original, DocumentId.parse("  " + original + "  "));
    }

    @Test
    public void rejectsInvalidExternalForms() {
        assertThrows(NullPointerException.class, () -> DocumentId.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DocumentId.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> DocumentId.parse("not-a-document-id"));
    }

    @Test
    public void rejectsNullUuid() {
        assertThrows(NullPointerException.class, () -> new DocumentId(null));
    }
}
