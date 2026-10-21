package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentOriginTest {
    @Test
    public void copiesSourcesAndExposesAnImmutableList() {
        var reference = new OriginReference(DocumentUri.parse("jar:file:///library.jar!/A.class"));
        var sources = new ArrayList<>(List.of(reference));
        var origin = new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.DECOMPILED, sources);
        sources.clear();

        assertEquals(List.of(reference), origin.sources());
        assertThrows(UnsupportedOperationException.class, () -> origin.sources().clear());
        assertTrue(new DocumentOrigin(BackingKind.FILE, ProvenanceKind.ORIGINAL).sources().isEmpty());
    }

    @Test
    public void rejectsMissingMetadataAndSources() {
        assertThrows(NullPointerException.class, () -> new DocumentOrigin(null, ProvenanceKind.ORIGINAL));
        assertThrows(NullPointerException.class, () -> new DocumentOrigin(BackingKind.FILE, null));
        assertThrows(NullPointerException.class,
            () -> new DocumentOrigin(BackingKind.FILE, ProvenanceKind.ORIGINAL, null));
        var sources = new ArrayList<OriginReference>();
        sources.add(null);
        assertThrows(NullPointerException.class,
            () -> new DocumentOrigin(BackingKind.FILE, ProvenanceKind.ORIGINAL, sources));
        assertThrows(NullPointerException.class, () -> new OriginReference(null));
    }

    @Test
    public void compatibilityOriginUsesLocationWithoutGuessingProvenance() {
        assertEquals(BackingKind.FILE, DocumentOrigin.unspecified(DocumentUri.parse("FILE:///A.java")).backing());
        assertEquals(BackingKind.MEMORY, DocumentOrigin.unspecified(DocumentUri.virtual("memory", "A.java")).backing());
        assertEquals(BackingKind.ARCHIVE_ENTRY,
            DocumentOrigin.unspecified(DocumentUri.parse("jar:file:///library.jar!/A.class")).backing());
        var origin = DocumentOrigin.unspecified(DocumentUri.virtual("generated", "A.java"));
        assertEquals(BackingKind.VIRTUAL, origin.backing());
        assertEquals(ProvenanceKind.UNSPECIFIED, origin.provenance());
        assertTrue(origin.sources().isEmpty());
        assertEquals(ProvenanceKind.ORIGINAL,
            DocumentOrigin.original(DocumentUri.virtual("memory", "A.java")).provenance());
        assertThrows(NullPointerException.class, () -> DocumentOrigin.unspecified(null));
    }
}
