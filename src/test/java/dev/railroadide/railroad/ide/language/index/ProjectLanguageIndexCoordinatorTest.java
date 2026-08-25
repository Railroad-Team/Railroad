package dev.railroadide.railroad.ide.language.index;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationManager;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
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
import java.nio.file.StandardWatchEventKinds;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ProjectLanguageIndexCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void warmIndexesBuildsRegisteredLanguageIndexes() throws Exception {
        Path root = createProject("""
            package demo;

            class A {
            }
            """);

        ProjectLanguageIndexService indexService = createIndexService();
        var coordinator = new ProjectLanguageIndexCoordinator(
            new DefaultProjectIndexContextResolver().resolve(new TestProject(root)),
            indexService,
            List.of(new JavaLanguageSupport()));

        coordinator.warmIndexes();

        JavaProjectSemanticIndex index = indexService.currentTyped(new TestProject(root),
            JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(index);
        assertEquals(1, index.lookupQualifiedName("demo.A").size());
    }

    @Test
    void handleFileChangeUpdatesAndRemovesIndexedFiles() throws Exception {
        Path root = createProject("""
            package demo;

            class A {
            }
            """);
        Path aFile = root.resolve("src/main/java/demo/A.java");

        ProjectLanguageIndexService indexService = createIndexService();
        var coordinator = new ProjectLanguageIndexCoordinator(
            new DefaultProjectIndexContextResolver().resolve(new TestProject(root)),
            indexService,
            List.of(new JavaLanguageSupport()));
        coordinator.warmIndexes();

        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_MODIFY);

        JavaProjectSemanticIndex updated = indexService.currentTyped(new TestProject(root),
            JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(updated);
        assertEquals(1, updated.lookupMember("demo.A", "VALUE").size());

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_DELETE);

        JavaProjectSemanticIndex removed = indexService.currentTyped(new TestProject(root),
            JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(removed);
        assertTrue(removed.lookupQualifiedName("demo.A").isEmpty());
    }

    private ProjectLanguageIndexService createIndexService() {
        LanguageSupport support = new JavaLanguageSupport();
        if (!LanguageSupportRegistry.contains(support.languageId())) {
            LanguageSupportRegistry.register(support);
        }

        var indexService = new ProjectLanguageIndexService();
        indexService.registerIndexer(support.createIndexer());
        indexService.registerPersistence(support.createPersistence());
        return indexService;
    }

    private Path createProject(String source) throws Exception {
        Path root = tempDir.resolve("project-" + System.nanoTime());
        Path file = root.resolve("src/main/java/demo/A.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return root;
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
