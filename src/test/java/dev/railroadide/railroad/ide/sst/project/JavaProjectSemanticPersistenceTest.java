package dev.railroadide.railroad.ide.sst.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JavaProjectSemanticPersistenceTest {

    @Test
    public void cancelledValidationPropagatesInsteadOfReportingACacheMiss() throws Exception {
        Path file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {} /*" + "padding ".repeat(4096) + "*/");
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(tempDir, new JavaProjectSemanticIndexer().build(tempDir));
        var checks = new AtomicInteger();
        var failure = new CancellationException("cancel cache validation");
        assertSame(failure, assertThrows(CancellationException.class,
            () -> persistence.loadIfCurrent(tempDir, List.of(file), () -> {
                if (checks.incrementAndGet() == 7)
                    throw failure;
            })));
        assertNotNull(persistence.loadIfCurrent(tempDir));
    }

    @Test
    public void cancelledSerializationKeepsPreviousSnapshotAndRemovesStagingFile() throws Exception {
        Path file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {}");
        var indexer = new JavaProjectSemanticIndexer();
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(tempDir, indexer.build(tempDir));
        Path snapshot = tempDir.resolve(".railroad/index/semantic/project-semantic-index.bin");
        byte[] previous = Files.readAllBytes(snapshot);
        var source = new StringBuilder("class A {");
        for (int index = 0; index < 100; index++) {
            source.append("int field").append(index).append(';');
        }
        Files.writeString(file, source.append('}'));
        var replacement = indexer.build(tempDir);
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> persistence.save(tempDir, replacement, Runnable::run, () -> {
            if (checks.incrementAndGet() == 20)
                throw new CancellationException();
        }));
        assertArrayEquals(previous, Files.readAllBytes(snapshot));
        try (var files = Files.walk(tempDir)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp")));
        }
    }

    @Test
    public void rejectedCommitLeavesNoSnapshotOrTemporaryFile() throws Exception {
        Path file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {}");
        var index = new JavaProjectSemanticIndexer().build(tempDir);
        new JavaProjectSemanticPersistence().save(tempDir, index, replacement -> {
        });
        assertNull(new JavaProjectSemanticPersistence().loadIfCurrent(tempDir));
        try (var files = Files.walk(tempDir)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp")));
        }
    }

    @TempDir
    public Path tempDir;

    @Test
    public void savesAndLoadsProjectSemanticIndex() throws Exception {
        Path root = tempDir.resolve("project");
        Path aFile = root.resolve("src/main/java/demo/A.java");
        Files.createDirectories(aFile.getParent());
        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        JavaProjectSemanticIndex index = new JavaProjectSemanticIndexer().build(root);
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(root, index);

        JavaProjectSemanticIndex loaded = persistence.loadIfCurrent(root);

        assertNotNull(loaded);
        assertEquals(1, loaded.files().size());
        assertEquals(1, loaded.lookupQualifiedName("demo.A").size());
        assertEquals(1, loaded.lookupMember("demo.A", "VALUE").size());
        var originalShard = index.getFile(aFile).orElseThrow().semanticShard();
        var loadedShard = loaded.getFile(aFile).orElseThrow().semanticShard();
        assertNotNull(loadedShard);
        assertEquals(originalShard.documentId(), loadedShard.documentId());
        assertEquals(originalShard.declarations().stream().map(symbol -> symbol.id()).toList(),
            loadedShard.declarations().stream().map(symbol -> symbol.id()).toList());
        for (var descriptor : loadedShard.declarations()) {
            assertFalse(loaded.graph().symbols(descriptor.id()).isEmpty());
        }
    }

    @Test
    public void ignoresStaleManifestEntries() throws Exception {
        Path root = tempDir.resolve("project");
        Path aFile = root.resolve("src/main/java/demo/A.java");
        Files.createDirectories(aFile.getParent());
        Files.writeString(aFile, """
            package demo;

            class A {
            }
            """);

        var indexer = new JavaProjectSemanticIndexer();
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(root, indexer.build(root));

        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        JavaProjectSemanticIndex loaded = persistence.loadIfCurrent(root);

        assertTrue(loaded == null);
    }

    @Test
    public void updatesPersistedSnapshotAfterOneFileChanges() throws Exception {
        Path root = tempDir.resolve("project");
        Path aFile = writeSource(root, "A.java", "class A {}");
        writeSource(root, "B.java", "class B {}");
        var indexer = new JavaProjectSemanticIndexer();
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(root, indexer.build(root));

        Files.writeString(aFile, "class A { static int VALUE; }");
        JavaProjectSemanticIndex updated = indexer.build(root);
        persistence.updateFile(root, updated, aFile);

        JavaProjectSemanticIndex loaded = persistence.loadIfCurrent(root);
        assertNotNull(loaded);
        assertEquals(2, loaded.files().size());
        assertEquals(1, loaded.lookupMember("A", "VALUE").size());
    }

    @Test
    public void removesDeletedFileFromPersistedSnapshot() throws Exception {
        Path root = tempDir.resolve("project");
        Path aFile = writeSource(root, "A.java", "class A {}");
        Path bFile = writeSource(root, "B.java", "class B {}");
        var indexer = new JavaProjectSemanticIndexer();
        var persistence = new JavaProjectSemanticPersistence();
        JavaProjectSemanticIndex initial = indexer.build(root);
        persistence.save(root, initial);

        Files.delete(bFile);
        JavaProjectSemanticIndex updated = JavaProjectSemanticIndex.builder()
            .putFile(initial.getFile(aFile).orElseThrow())
            .build();
        persistence.removeFile(root, updated, bFile);

        JavaProjectSemanticIndex loaded = persistence.loadIfCurrent(root);
        assertNotNull(loaded);
        assertEquals(1, loaded.files().size());
        assertTrue(loaded.lookupQualifiedName("B").isEmpty());
    }

    @Test
    public void neverAssignsNewDiskHashToPreviouslyParsedDeclarations() throws Exception {
        Path root = tempDir.resolve("project");
        Path file = writeSource(root, "A.java", "class A { int oldField; }");
        JavaProjectSemanticIndex parsed = new JavaProjectSemanticIndexer().build(root);

        Files.writeString(file, "class A { int newField; }");
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(root, parsed);

        assertNull(persistence.loadIfCurrent(root));
    }

    @Test
    public void sameTimestampUpdateDoesNotReusePreviousHash() throws Exception {
        Path root = tempDir.resolve("project");
        Path file = writeSource(root, "A.java", "class A { int oldField; }");
        FileTime originalTime = Files.getLastModifiedTime(file);
        var indexer = new JavaProjectSemanticIndexer();
        var persistence = new JavaProjectSemanticPersistence();
        persistence.save(root, indexer.build(root));

        Files.writeString(file, "class A { int newField; }");
        Files.setLastModifiedTime(file, originalTime);
        JavaProjectSemanticIndex updated = indexer.build(root);
        persistence.save(root, updated);

        JavaProjectSemanticIndex loaded = persistence.loadIfCurrent(root);
        assertNotNull(loaded);
        assertEquals(1, loaded.lookupMember("A", "newField").size());
        assertTrue(loaded.lookupMember("A", "oldField").isEmpty());
        persistence.save(root, loaded);
        assertNotNull(persistence.loadIfCurrent(root));
    }

    @Test
    public void refusesEntriesWithoutParsedInputProvenance() throws Exception {
        Path root = tempDir.resolve("project");
        Path file = writeSource(root, "A.java", "class A {}");
        JavaProjectSemanticIndex.SourceFileIndex parsed = new JavaProjectSemanticIndexer().indexFile(file);
        var unprovenanced = new JavaProjectSemanticIndex.SourceFileIndex(
            parsed.path(), parsed.packageName(), parsed.imports(), parsed.declaredSymbols());
        var persistence = new JavaProjectSemanticPersistence();

        persistence.save(root, JavaProjectSemanticIndex.builder().putFile(unprovenanced).build());

        assertNull(persistence.loadIfCurrent(root));
    }

    @Test
    public void doesNotPersistPartialIndexWhenAnIndexedFileDisappears() throws Exception {
        Path root = tempDir.resolve("project");
        Path file = writeSource(root, "A.java", "class A {}");
        writeSource(root, "B.java", "class B {}");
        JavaProjectSemanticIndex parsed = new JavaProjectSemanticIndexer().build(root);
        Files.delete(file);
        var persistence = new JavaProjectSemanticPersistence();

        persistence.save(root, parsed);

        assertNull(persistence.loadIfCurrent(root));
    }

    @Test
    public void rejectsCachesWrittenBeforeParsedInputProvenanceWasRequired() throws Exception {
        Path root = tempDir.resolve("project");
        Path snapshot = root.resolve(".railroad/index/semantic/project-semantic-index.bin");
        Files.createDirectories(snapshot.getParent());
        try (var output = new DataOutputStream(Files.newOutputStream(snapshot))) {
            output.writeUTF("RSSTIDX1");
            output.writeInt(3);
            output.writeInt(0);
        }

        assertNull(new JavaProjectSemanticPersistence().loadIfCurrent(root));
    }

    private static Path writeSource(Path root, String name, String source) throws Exception {
        Path file = root.resolve(name);
        Files.createDirectories(root);
        Files.writeString(file, source);
        return file;
    }

}
