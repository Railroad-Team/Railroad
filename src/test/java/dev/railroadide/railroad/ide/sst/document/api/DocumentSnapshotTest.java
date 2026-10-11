package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentSnapshotTest {

    @Test
    void rootIsSealedToTextAndBinarySnapshots() {
        assertTrue(DocumentSnapshot.class.isSealed());
        assertEquals(
            Set.of(TextDocumentSnapshot.class, BinaryDocumentSnapshot.class),
            Set.of(DocumentSnapshot.class.getPermittedSubclasses()));
    }

    @Test
    void textSnapshotCopiesMutableInputAndCarriesMetadata() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.virtual("generated", "sources/Example.java");
        var version = new DocumentVersion(8);
        var source = new StringBuilder("class Example {}");
        var origin = new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED);

        var snapshot = new TextDocumentSnapshot(
            id,
            uri,
            version,
            "java",
            origin,
            source,
            StandardCharsets.UTF_16);
        source.replace(0, source.length(), "changed");

        assertEquals(id, snapshot.id());
        assertEquals(uri, snapshot.uri());
        assertEquals(version, snapshot.version());
        assertEquals("java", snapshot.languageId());
        assertEquals("class Example {}", snapshot.text());
        assertEquals(StandardCharsets.UTF_16, snapshot.encoding());
        assertEquals(ContentKind.TEXT, snapshot.contentKind());
        assertSame(origin, snapshot.origin());
    }

    @Test
    void textSnapshotAlsoAcceptsPhysicalDocumentLocations() {
        DocumentUri physicalUri = DocumentUri.fromPath(Path.of("Example.java").toAbsolutePath());

        var snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            physicalUri,
            DocumentVersion.initial(),
            "java",
            "class Example {}",
            StandardCharsets.UTF_8);

        assertTrue(snapshot.uri().isFile());
        assertEquals(new DocumentOrigin(BackingKind.FILE, ProvenanceKind.UNSPECIFIED), snapshot.origin());
    }

    @Test
    void binarySnapshotCopiesInputsAndExposesIndependentReadOnlyViews() {
        byte[] content = {1, 2, 3, 4};
        var snapshot = new BinaryDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("memory", "images/icon.bin"),
            DocumentVersion.initial(),
            "application/octet-stream",
            content);
        content[0] = (byte) 99;

        ByteBuffer first = snapshot.bytes();
        ByteBuffer second = snapshot.bytes();
        assertTrue(first.isReadOnly());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, snapshot.copyBytes());
        assertEquals(4, snapshot.size());
        assertEquals(ContentKind.BINARY, snapshot.contentKind());
        assertEquals(new DocumentOrigin(BackingKind.MEMORY, ProvenanceKind.UNSPECIFIED), snapshot.origin());
        assertEquals(1, first.get());
        assertEquals(0, second.position());
        assertThrows(ReadOnlyBufferException.class, () -> second.put((byte) 9));

        byte[] copy = snapshot.copyBytes();
        copy[1] = (byte) 88;
        assertArrayEquals(new byte[]{1, 2, 3, 4}, snapshot.copyBytes());
    }

    @Test
    void binarySnapshotCopiesOnlyRemainingBytesWithoutMovingSourcePosition() {
        ByteBuffer source = ByteBuffer.wrap(new byte[]{10, 20, 30, 40});
        source.position(1);
        source.limit(3);

        var snapshot = new BinaryDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("archive", "library.jar!/asset.dat"),
            new DocumentVersion(2),
            "binary",
            source);

        assertEquals(1, source.position());
        assertArrayEquals(new byte[]{20, 30}, snapshot.copyBytes());
        assertEquals(new DocumentOrigin(BackingKind.ARCHIVE_ENTRY, ProvenanceKind.UNSPECIFIED), snapshot.origin());
    }

    @Test
    void snapshotsRejectMissingMetadataAndBlankLanguages() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.inMemory(id);
        DocumentVersion version = DocumentVersion.initial();

        assertThrows(IllegalArgumentException.class, () -> new TextDocumentSnapshot(
            id, uri, version, "  ", "", StandardCharsets.UTF_8));
        assertThrows(NullPointerException.class, () -> new TextDocumentSnapshot(
            id, uri, version, "java", null, StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> new BinaryDocumentSnapshot(
            id, uri, version, "\t", new byte[0]));
        assertThrows(NullPointerException.class, () -> new BinaryDocumentSnapshot(
            id, uri, version, "binary", (byte[]) null));
        assertThrows(NullPointerException.class, () -> new TextDocumentSnapshot(
            id, uri, version, "java", null, "", StandardCharsets.UTF_8));
        assertThrows(NullPointerException.class, () -> new BinaryDocumentSnapshot(
            id, uri, version, "binary", null, new byte[0]));
    }

    @Test
    void binaryConstructorsRetainExplicitOriginForUnregisteredLanguage() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.inMemory(id);
        var origin = new DocumentOrigin(BackingKind.MEMORY, ProvenanceKind.GENERATED);
        byte[] content = {1, 2};

        var arraySnapshot = new BinaryDocumentSnapshot(id, uri, DocumentVersion.initial(),
            "test:custom-binary", origin, content);
        var bufferSnapshot = new BinaryDocumentSnapshot(id, uri, DocumentVersion.initial(),
            "test:custom-binary", origin, ByteBuffer.wrap(content));

        assertSame(origin, arraySnapshot.origin());
        assertSame(origin, bufferSnapshot.origin());
        assertEquals("test:custom-binary", arraySnapshot.languageId());
        assertArrayEquals(arraySnapshot.copyBytes(), bufferSnapshot.copyBytes());
    }

    @Test
    void explicitOriginsKeepBackingIndependentOfProvenance() {
        var generated = new DocumentOrigin(BackingKind.FILE, ProvenanceKind.GENERATED);
        var attached = new DocumentOrigin(BackingKind.ARCHIVE_ENTRY, ProvenanceKind.ATTACHED_SOURCE);
        var generatedSnapshot = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.fromPath(Path.of("build/generated/Example.java")), DocumentVersion.initial(),
            "java", generated, "class Example {}", StandardCharsets.UTF_8);
        var attachedSnapshot = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.parse("jar:file:///library-sources.jar!/Example.java"), DocumentVersion.initial(),
            "java", attached, "class Example {}", StandardCharsets.UTF_8);

        assertSame(generated, generatedSnapshot.origin());
        assertTrue(generatedSnapshot.uri().isFile());
        assertSame(attached, attachedSnapshot.origin());
        assertEquals(ContentKind.TEXT, attachedSnapshot.contentKind());
    }

    @Test
    void legacyConstructorsDoNotInferProvenanceFromVirtualSchemes() {
        for (String scheme : Set.of("generated", "decompiled")) {
            DocumentUri uri = DocumentUri.virtual(scheme, "Example.java");
            var text = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(),
                "java", "class Example {}", StandardCharsets.UTF_8);
            var bytes = new BinaryDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(),
                "binary", new byte[]{1});
            var buffer = new BinaryDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(),
                "binary", ByteBuffer.wrap(new byte[]{1}));
            var expected = new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.UNSPECIFIED);

            assertEquals(expected, text.origin());
            assertEquals(expected, bytes.origin());
            assertEquals(expected, buffer.origin());
        }
    }
}
