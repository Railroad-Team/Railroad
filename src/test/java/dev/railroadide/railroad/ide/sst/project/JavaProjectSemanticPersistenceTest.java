package dev.railroadide.railroad.ide.sst.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaProjectSemanticPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsProjectSemanticIndex() throws Exception {
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
    }

    @Test
    void ignoresStaleManifestEntries() throws Exception {
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
    void updatesPersistedSnapshotAfterOneFileChanges() throws Exception {
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
    void removesDeletedFileFromPersistedSnapshot() throws Exception {
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

    private static Path writeSource(Path root, String name, String source) throws Exception {
        Path file = root.resolve(name);
        Files.createDirectories(root);
        Files.writeString(file, source);
        return file;
    }

}
