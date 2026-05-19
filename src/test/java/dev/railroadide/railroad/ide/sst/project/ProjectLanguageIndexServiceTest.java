package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.language.impl.index.JavaProjectLanguageIndexer;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectLanguageIndexServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void cachesIndexesPerProjectRoot() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """
        );

        TestJavaProjectIndexAccess service = new TestJavaProjectIndexAccess();
        JavaProjectSemanticIndex first = service.index(root);
        JavaProjectSemanticIndex second = service.index(root);

        assertSame(first, second);
        assertTrue(service.hasIndex(root));
    }

    @Test
    void rebuildRefreshesProjectIndex() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """
        );

        TestJavaProjectIndexAccess service = new TestJavaProjectIndexAccess();
        JavaProjectSemanticIndex initial = service.index(root);

        Files.writeString(root.resolve("src/main/java/demo/B.java"), """
            package demo;

            class B {
            }
            """);

        JavaProjectSemanticIndex rebuilt = service.rebuild(root);

        assertNotSame(initial, rebuilt);
        assertEquals(2, rebuilt.files().size());
        assertEquals(1, rebuilt.lookupQualifiedName("demo.B").size());
    }

    @Test
    void updateFileReplacesOneIndexedFile() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """
        );

        TestJavaProjectIndexAccess service = new TestJavaProjectIndexAccess();
        service.index(root);

        Path aFile = root.resolve("src/main/java/demo/A.java");
        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        service.updateFile(root, aFile);
        JavaProjectSemanticIndex updated = service.index(root);

        assertEquals(1, updated.lookupMember("demo.A", "VALUE").size());
    }

    @Test
    void removeFileDropsIndexedFacts() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """,
            "src/main/java/demo/B.java", """
                package demo;

                class B {
                }
                """
        );

        TestJavaProjectIndexAccess service = new TestJavaProjectIndexAccess();
        service.index(root);

        Path bFile = root.resolve("src/main/java/demo/B.java");
        service.removeFile(root, bFile);

        JavaProjectSemanticIndex updated = service.index(root);
        assertEquals(1, updated.files().size());
        assertTrue(updated.lookupQualifiedName("demo.B").isEmpty());
    }

    @Test
    void invalidateRemovesInMemoryCacheOnly() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """
        );

        TestJavaProjectIndexAccess service = new TestJavaProjectIndexAccess();
        JavaProjectSemanticIndex initial = service.index(root);
        assertTrue(service.hasIndex(root));

        service.invalidate(root);
        assertFalse(service.hasIndex(root));

        JavaProjectSemanticIndex reloaded = service.index(root);
        assertNotSame(initial, reloaded);
        assertEquals(1, reloaded.lookupQualifiedName("demo.A").size());
    }

    @Test
    void loadsPersistedIndexAfterCacheMiss() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """
        );

        TestJavaProjectIndexAccess writer = new TestJavaProjectIndexAccess();
        JavaProjectSemanticIndex initial = writer.index(root);

        TestJavaProjectIndexAccess reader = new TestJavaProjectIndexAccess();
        JavaProjectSemanticIndex reloaded = reader.index(root);

        assertNotSame(initial, reloaded);
        assertEquals(1, reloaded.lookupQualifiedName("demo.A").size());
    }

    @Test
    void reloadsFromSourceWhenPersistedIndexIsStale() throws Exception {
        Path root = createProject(
            "src/main/java/demo/A.java", """
                package demo;

                class A {
                }
                """
        );

        TestJavaProjectIndexAccess writer = new TestJavaProjectIndexAccess();
        writer.index(root);

        Path aFile = root.resolve("src/main/java/demo/A.java");
        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        TestJavaProjectIndexAccess reader = new TestJavaProjectIndexAccess();
        JavaProjectSemanticIndex reloaded = reader.index(root);

        assertEquals(1, reloaded.lookupMember("demo.A", "VALUE").size());
    }

    private Path createProject(String relativePath, String source, String... additionalPathAndSourcePairs) throws Exception {
        Path root = tempDir.resolve("project-" + System.nanoTime());
        writeProjectSource(root, relativePath, source);
        for (int index = 0; index < additionalPathAndSourcePairs.length; index += 2) {
            writeProjectSource(root, additionalPathAndSourcePairs[index], additionalPathAndSourcePairs[index + 1]);
        }
        return root;
    }

    private static void writeProjectSource(Path root, String relativePath, String source) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private static final class TestJavaProjectIndexAccess {
        private static final String LANGUAGE_ID = "java";

        private final ProjectLanguageIndexService indexService = new ProjectLanguageIndexService();

        private TestJavaProjectIndexAccess() {
            indexService.registerIndexer(new JavaProjectLanguageIndexer());
            indexService.registerPersistence(new JavaProjectSemanticPersistence());
        }

        private JavaProjectSemanticIndex index(Path projectRoot) {
            JavaProjectSemanticIndex rebuilt = indexService.indexTyped(normalize(projectRoot), LANGUAGE_ID);
            if (rebuilt == null)
                throw new IllegalStateException("No indexer registered for " + LANGUAGE_ID);
            return rebuilt;
        }

        private JavaProjectSemanticIndex current(Path projectRoot) {
            return indexService.currentTyped(normalize(projectRoot), LANGUAGE_ID);
        }

        private JavaProjectSemanticIndex rebuild(Path projectRoot) {
            JavaProjectSemanticIndex rebuilt = indexService.rebuildTyped(normalize(projectRoot), LANGUAGE_ID);
            if (rebuilt == null)
                throw new IllegalStateException("No indexer registered for " + LANGUAGE_ID);
            return rebuilt;
        }

        private JavaProjectSemanticIndex.SourceFileIndex updateFile(Path projectRoot, Path file) {
            Path normalizedRoot = normalize(projectRoot);
            Path normalizedFile = normalize(file);
            JavaProjectSemanticIndex.SourceFileIndex indexedFile =
                indexService.updateFile(normalizedRoot, LANGUAGE_ID, normalizedFile);
            if (indexedFile == null) {
                JavaProjectSemanticIndex rebuilt = rebuild(normalizedRoot);
                return rebuilt.getFile(normalizedFile)
                    .orElseThrow(() -> new IllegalStateException("Rebuilt index missing " + normalizedFile));
            }

            return indexedFile;
        }

        private void removeFile(Path projectRoot, Path file) {
            Path normalizedRoot = normalize(projectRoot);
            if (!hasIndex(normalizedRoot))
                return;

            indexService.removeFile(normalizedRoot, LANGUAGE_ID, normalize(file));
        }

        private void invalidate(Path projectRoot) {
            Path normalizedRoot = normalize(projectRoot);
            indexService.invalidate(normalizedRoot, LANGUAGE_ID);
        }

        private boolean hasIndex(Path projectRoot) {
            return indexService.currentTyped(normalize(projectRoot), LANGUAGE_ID) != null;
        }

        private static Path normalize(Path path) {
            return path.toAbsolutePath().normalize();
        }
    }
}
