package dev.railroadide.railroad.ide.sst.project;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.index.JavaProjectLanguageIndexer;
import dev.railroadide.railroad.ide.language.index.DefaultProjectIndexContextResolver;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationManager;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetType;
import dev.railroadide.railroad.vcs.git.GitManager;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
        private final DefaultProjectIndexContextResolver contextResolver = new DefaultProjectIndexContextResolver();

        private TestJavaProjectIndexAccess() {
            if (!LanguageSupportRegistry.contains(JavaLanguageSupport.LANGUAGE_ID)) {
                LanguageSupportRegistry.register(new JavaLanguageSupport());
            }

            indexService.registerIndexer(new JavaProjectLanguageIndexer());
            indexService.registerPersistence(new JavaProjectSemanticPersistence());
        }

        private JavaProjectSemanticIndex index(Path projectRoot) {
            JavaProjectSemanticIndex rebuilt = indexService.indexTyped(project(projectRoot), LANGUAGE_ID);
            if (rebuilt == null)
                throw new IllegalStateException("No indexer registered for " + LANGUAGE_ID);
            return rebuilt;
        }

        private JavaProjectSemanticIndex current(Path projectRoot) {
            return indexService.currentTyped(project(projectRoot), LANGUAGE_ID);
        }

        private JavaProjectSemanticIndex rebuild(Path projectRoot) {
            JavaProjectSemanticIndex rebuilt = indexService.rebuildTyped(project(projectRoot), LANGUAGE_ID);
            if (rebuilt == null)
                throw new IllegalStateException("No indexer registered for " + LANGUAGE_ID);
            return rebuilt;
        }

        private JavaProjectSemanticIndex.SourceFileIndex updateFile(Path projectRoot, Path file) {
            Path normalizedRoot = normalize(projectRoot);
            Path normalizedFile = normalize(file);
            JavaProjectSemanticIndex.SourceFileIndex indexedFile =
                indexService.updateFile(context(normalizedRoot), LANGUAGE_ID, normalizedFile);
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

            indexService.removeFile(context(normalizedRoot), LANGUAGE_ID, normalize(file));
        }

        private void invalidate(Path projectRoot) {
            indexService.invalidate(project(projectRoot), LANGUAGE_ID);
        }

        private boolean hasIndex(Path projectRoot) {
            return current(projectRoot) != null;
        }

        private static Path normalize(Path path) {
            return path.toAbsolutePath().normalize();
        }

        private Project project(Path projectRoot) {
            return new TestProject(normalize(projectRoot));
        }

        private ProjectIndexContext context(Path projectRoot) {
            return contextResolver.resolve(project(projectRoot));
        }

        private record TestProject(Path path) implements Project {
            @Override
            public String getAlias() {
                return path.getFileName() != null ? path.getFileName().toString() : path.toString();
            }

            @Override
            public void setAlias(String alias) {
                throw unsupported();
            }

            @Override
            public boolean hasFacet(FacetType<?> type) {
                return false;
            }

            @Override
            public <D> Optional<Facet<D>> getFacet(FacetType<D> type) {
                return Optional.empty();
            }

            @Override
            public void open() {
                throw unsupported();
            }

            @Override
            public String getId() {
                return getPathString();
            }

            @Override
            public long getLastOpened() {
                return 0L;
            }

            @Override
            public void setLastOpened(long timestamp) {
                throw unsupported();
            }

            @Override
            public List<Facet<?>> getFacets() {
                return List.of();
            }

            @Override
            public CompletableFuture<Runnable> build(JDK jdk) {
                throw unsupported();
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public void setDescription(String description) {
                throw unsupported();
            }

            @Override
            public License getLicense() {
                throw unsupported();
            }

            @Override
            public void setLicense(License license) {
                throw unsupported();
            }

            @Override
            public GitManager getGitManager() {
                throw unsupported();
            }

            @Override
            public RunConfigurationManager getRunConfigManager() {
                throw unsupported();
            }

            @Override
            public DebuggingManager getDebuggingManager() {
                throw unsupported();
            }

            @Override
            public ProjectDataStore getDataStore() {
                throw unsupported();
            }

            @Override
            public GradleManager getGradleManager() {
                throw unsupported();
            }

            @Override
            public Image getIcon() {
                throw unsupported();
            }

            @Override
            public void setIcon(Image icon) {
                throw unsupported();
            }

            @Override
            public JsonObject toJson() {
                throw unsupported();
            }

            @Override
            public void fromJson(JsonObject json) {
                throw unsupported();
            }

            private static UnsupportedOperationException unsupported() {
                return new UnsupportedOperationException("Test project only exposes the project path.");
            }
        }
    }
}
