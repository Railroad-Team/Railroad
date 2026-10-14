package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class DocumentSnapshotTest {

    @Test
    void lineMapIsCachedPerSnapshot() {
        DocumentId id = DocumentId.create();
        var snapshot = new TextDocumentSnapshot(
            id,
            DocumentUri.inMemory(id),
            DocumentVersion.initial(),
            "java",
            "Hello\nWorld",
            StandardCharsets.UTF_8);

        LineMap first = snapshot.lineMap();
        LineMap second = snapshot.lineMap();

        assertSame(first, second);
    }

    @Test
    void differentSnapshotsHaveIndependentLineMaps() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.inMemory(id);

        var first = new TextDocumentSnapshot(
            id,
            uri,
            DocumentVersion.initial(),
            "java",
            "Hello\nWorld",
            StandardCharsets.UTF_8);

        var second = new TextDocumentSnapshot(
            id,
            uri,
            DocumentVersion.initial().next(),
            "java",
            "Hello\nWorld\n!",
            StandardCharsets.UTF_8);

        assertNotSame(first.lineMap(), second.lineMap());
        assertEquals(2, first.lineMap().lineCount());
        assertEquals(3, second.lineMap().lineCount());
    }

    @Test
    void concurrentLineMapAccessReturnsSameCachedInstance() throws Exception {
        DocumentId id = DocumentId.create();
        var snapshot = new TextDocumentSnapshot(
            id,
            DocumentUri.inMemory(id),
            DocumentVersion.initial(),
            "java",
            "a\r\nb\nc",
            StandardCharsets.UTF_8);

        try (var executor = Executors.newFixedThreadPool(4)) {
            var ready = new CountDownLatch(4);
            var start = new CountDownLatch(1);

            List<Future<LineMap>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return snapshot.lineMap();
                }));
            }

            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            LineMap first = futures.getFirst().get(10, TimeUnit.SECONDS);
            for (Future<LineMap> future : futures) {
                assertSame(first, future.get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void cachesLineMap() {
        var snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("memory", "Test.java"),
            DocumentVersion.initial(),
            "java",
            "Hello\nWorld",
            StandardCharsets.UTF_8);

        LineMap first = snapshot.lineMap();
        LineMap second = snapshot.lineMap();

        assertSame(first, second);
        assertEquals(2, first.lineCount());
    }

    @Test
    void referenceEqualityAndRevisionIdentityDoNotConflateMetadata() {
        DocumentId id = DocumentId.create();
        DocumentUri uri = DocumentUri.inMemory(id);
        var version = DocumentVersion.initial();
        var first = new TextDocumentSnapshot(id, uri, version, "java", "old", StandardCharsets.UTF_8);
        var sameState = new TextDocumentSnapshot(id, uri, version, "java", "old", StandardCharsets.UTF_8);
        var renamed = new TextDocumentSnapshot(id, DocumentUri.virtual("memory", "renamed.java"), version,
            "java", new DocumentOrigin(BackingKind.MEMORY, ProvenanceKind.GENERATED), "old", StandardCharsets.UTF_8);
        var newer = new TextDocumentSnapshot(id, uri, version.next(), "java", "new", StandardCharsets.UTF_8);
        var unrelated = new TextDocumentSnapshot(DocumentId.create(), uri, version, "java", "old",
            StandardCharsets.UTF_8);

        assertEquals(first, first);
        assertNotEquals(first, sameState);
        assertNotEquals(first, renamed);
        assertEquals(3, Set.of(first, sameState, renamed).size());
        assertTrue(first.hasSameRevision(sameState));
        assertTrue(first.hasSameRevision(renamed));
        assertFalse(first.hasSameRevision(newer));
        assertFalse(first.hasSameRevision(unrelated));
        assertThrows(NullPointerException.class, () -> first.hasSameRevision(null));
        assertEquals("old", first.text());
        assertEquals(uri, first.uri());
        assertEquals(version, first.version());
    }

    @Test
    void retainedTextAndBinarySnapshotsSupportConcurrentReadsAfterOwnerReplacement() throws Exception {
        DocumentId id = DocumentId.create();
        var uri = DocumentUri.inMemory(id);
        var version = DocumentVersion.initial();
        var input = new StringBuilder("original");
        var old = new TextDocumentSnapshot(id, uri, version, "text", input, StandardCharsets.UTF_8);
        var binary = new BinaryDocumentSnapshot(id, uri, version, "binary", new byte[]{1, 2, 3});
        var owned = new ArrayList<DocumentSnapshot>();
        owned.add(old);
        input.replace(0, input.length(), "edited");
        owned.set(0, new TextDocumentSnapshot(id, uri, version.next(), "text", input, StandardCharsets.UTF_8));
        owned.clear(); // Closing/removing an owner releases its reference, without invalidation.
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int read = 0; read < 20; read++) {
                        assertEquals("original", old.text());
                        assertEquals(version, old.version());
                        var view = binary.bytes();
                        assertEquals(0, view.position());
                        assertEquals(1, view.get());
                        assertArrayEquals(new byte[]{1, 2, 3}, binary.copyBytes());
                    }
                    return null;
                }));
            }
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            for (var task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        }
    }

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
