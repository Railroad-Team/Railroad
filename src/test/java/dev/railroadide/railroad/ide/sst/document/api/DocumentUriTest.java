package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentUriTest {
    @TempDir
    public Path tempDirectory;

    @Test
    public void physicalPathRoundTripsThroughFileUri() {
        Path path = tempDirectory.resolve("folder").resolve("..").resolve("Document.java");

        DocumentUri uri = DocumentUri.fromPath(path);

        assertTrue(uri.isFile());
        assertEquals(path.toAbsolutePath().normalize(), uri.filePath().orElseThrow());
        assertEquals(uri, DocumentUri.parse(uri.toString()));
    }

    @Test
    public void supportsHierarchicalVirtualDocumentSchemes() {
        DocumentUri uri = DocumentUri.virtual("GENERATED", "sources/My File.java");

        assertEquals("generated", uri.value().getScheme());
        assertEquals("/sources/My File.java", uri.value().getPath());
        assertEquals("generated:/sources/My%20File.java", uri.toString());
        assertFalse(uri.isFile());
        assertTrue(uri.filePath().isEmpty());
    }

    @Test
    public void supportsOpaqueArchiveEntryUris() {
        DocumentUri uri = DocumentUri.parse("jar:file:///libraries/example.jar!/demo/Example.class");

        assertEquals("jar", uri.value().getScheme());
        assertTrue(uri.value().isOpaque());
        assertTrue(uri.filePath().isEmpty());
    }

    @Test
    public void inMemoryLocationIsStableForTheSuppliedDocumentId() {
        DocumentId documentId = DocumentId.create();

        assertEquals(DocumentUri.inMemory(documentId), DocumentUri.inMemory(documentId));
        assertEquals("memory", DocumentUri.inMemory(documentId).value().getScheme());
    }

    @Test
    public void rejectsNullBlankMalformedAndRelativeUris() {
        assertThrows(NullPointerException.class, () -> DocumentUri.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DocumentUri.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> DocumentUri.parse("not a URI"));
        assertThrows(IllegalArgumentException.class, () -> DocumentUri.parse("relative/document.java"));
        assertThrows(IllegalArgumentException.class, () -> new DocumentUri(URI.create("relative")));
        assertThrows(IllegalArgumentException.class, () -> DocumentUri.virtual("bad scheme", "document"));
    }
}
