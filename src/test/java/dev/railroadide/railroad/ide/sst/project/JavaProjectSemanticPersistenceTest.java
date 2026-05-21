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
        JavaProjectSemanticPersistence persistence = new JavaProjectSemanticPersistence();
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

        JavaProjectSemanticIndexer indexer = new JavaProjectSemanticIndexer();
        JavaProjectSemanticPersistence persistence = new JavaProjectSemanticPersistence();
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
}
