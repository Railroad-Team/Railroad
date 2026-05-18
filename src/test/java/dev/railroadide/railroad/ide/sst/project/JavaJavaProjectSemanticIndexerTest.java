package dev.railroadide.railroad.ide.sst.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaJavaProjectSemanticIndexerTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsProjectIndexAcrossMultipleFiles() throws Exception {
        Path root = tempDir.resolve("project");
        Path sourceRoot = root.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);

        Path aFile = sourceRoot.resolve("A.java");
        Path bFile = sourceRoot.resolve("B.java");

        Files.writeString(aFile, """
                package demo;

                class A {
                    static int VALUE;
                }
                """);

        Files.writeString(bFile, """
                package demo;

                import demo.A;
                import static demo.A.VALUE;

                class B {
                    int use() {
                        return VALUE;
                    }
                }
                """);

        JavaProjectSemanticIndexer indexer = new JavaProjectSemanticIndexer();
        JavaProjectSemanticIndex index = indexer.build(root);

        assertEquals(2, index.files().size());
        assertTrue(index.getFile(aFile).isPresent());
        assertTrue(index.getFile(bFile).isPresent());
        assertEquals(2, index.getFilesByPackage("demo").size());
        assertEquals(1, index.lookupQualifiedName("demo.A").size());
        assertEquals(1, index.lookupQualifiedName("demo.B").size());
        assertEquals(1, index.lookupMember("demo.A", "VALUE").size());
    }
}
