package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentVersionTest {
    @Test
    public void initialVersionStartsAtZero() {
        assertEquals(0, DocumentVersion.initial().value());
    }

    @Test
    public void nextProducesAStrictlyLaterVersion() {
        var current = new DocumentVersion(41);
        DocumentVersion next = current.next();

        assertEquals(new DocumentVersion(42), next);
        assertTrue(next.isAfter(current));
        assertTrue(current.isBefore(next));
        assertFalse(current.isAfter(next));
    }

    @Test
    public void decimalExternalFormRoundTrips() {
        var version = new DocumentVersion(123456789);

        assertEquals("123456789", version.toString());
        assertEquals(version, DocumentVersion.parse(version.toString()));
        assertEquals(version, DocumentVersion.parse("  123456789  "));
    }

    @Test
    public void rejectsInvalidVersions() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentVersion(-1));
        assertThrows(NullPointerException.class, () -> DocumentVersion.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DocumentVersion.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> DocumentVersion.parse("1.5"));
        assertThrows(IllegalArgumentException.class, () -> DocumentVersion.parse("-1"));
    }

    @Test
    public void failsExplicitlyAtVersionExhaustion() {
        assertThrows(IllegalStateException.class, () -> new DocumentVersion(Long.MAX_VALUE).next());
    }
}
