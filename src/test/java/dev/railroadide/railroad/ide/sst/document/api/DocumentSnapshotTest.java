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
            Set.of(DocumentSnapshot.class.getPermittedSubclasses())
        );
    }

    @Test
    void textSnapshotCopiesMutableInputAndCarriesMetadata() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.virtual("generated", "sources/Example.java");
        DocumentVersion version = new DocumentVersion(8);
        StringBuilder source = new StringBuilder("class Example {}");

        TextDocumentSnapshot snapshot = new TextDocumentSnapshot(
            id,
            uri,
            version,
            "java",
            source,
            StandardCharsets.UTF_16
        );
        source.replace(0, source.length(), "changed");

        assertEquals(id, snapshot.id());
        assertEquals(uri, snapshot.uri());
        assertEquals(version, snapshot.version());
        assertEquals("java", snapshot.languageId());
        assertEquals("class Example {}", snapshot.text());
        assertEquals(StandardCharsets.UTF_16, snapshot.encoding());
    }

    @Test
    void textSnapshotAlsoAcceptsPhysicalDocumentLocations() {
        DocumentUri physicalUri = DocumentUri.fromPath(Path.of("Example.java").toAbsolutePath());

        TextDocumentSnapshot snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            physicalUri,
            DocumentVersion.initial(),
            "java",
            "class Example {}",
            StandardCharsets.UTF_8
        );

        assertTrue(snapshot.uri().isFile());
    }

    @Test
    void binarySnapshotCopiesInputsAndExposesIndependentReadOnlyViews() {
        byte[] content = {1, 2, 3, 4};
        BinaryDocumentSnapshot snapshot = new BinaryDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("memory", "images/icon.bin"),
            DocumentVersion.initial(),
            "application/octet-stream",
            content
        );
        content[0] = (byte) 99;

        ByteBuffer first = snapshot.bytes();
        ByteBuffer second = snapshot.bytes();
        assertTrue(first.isReadOnly());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, snapshot.copyBytes());
        assertEquals(4, snapshot.size());
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

        BinaryDocumentSnapshot snapshot = new BinaryDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("archive", "library.jar!/asset.dat"),
            new DocumentVersion(2),
            "binary",
            source
        );

        assertEquals(1, source.position());
        assertArrayEquals(new byte[]{20, 30}, snapshot.copyBytes());
    }

    @Test
    void snapshotsRejectMissingMetadataAndBlankLanguages() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.inMemory(id);
        DocumentVersion version = DocumentVersion.initial();

        assertThrows(IllegalArgumentException.class, () -> new TextDocumentSnapshot(
            id, uri, version, "  ", "", StandardCharsets.UTF_8
        ));
        assertThrows(NullPointerException.class, () -> new TextDocumentSnapshot(
            id, uri, version, "java", null, StandardCharsets.UTF_8
        ));
        assertThrows(IllegalArgumentException.class, () -> new BinaryDocumentSnapshot(
            id, uri, version, "\t", new byte[0]
        ));
        assertThrows(NullPointerException.class, () -> new BinaryDocumentSnapshot(
            id, uri, version, "binary", (byte[]) null
        ));
    }
}
