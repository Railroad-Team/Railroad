package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;

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
        ProjectLanguageIndexCoordinator coordinator = new ProjectLanguageIndexCoordinator(root, indexService, List.of(new JavaLanguageSupport()));

        coordinator.warmIndexes();

        JavaProjectSemanticIndex index = indexService.currentTyped(root, JavaLanguageSupport.LANGUAGE_ID);
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
        ProjectLanguageIndexCoordinator coordinator = new ProjectLanguageIndexCoordinator(root, indexService, List.of(new JavaLanguageSupport()));
        coordinator.warmIndexes();

        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_MODIFY);

        JavaProjectSemanticIndex updated = indexService.currentTyped(root, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(updated);
        assertEquals(1, updated.lookupMember("demo.A", "VALUE").size());

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_DELETE);

        JavaProjectSemanticIndex removed = indexService.currentTyped(root, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(removed);
        assertTrue(removed.lookupQualifiedName("demo.A").isEmpty());
    }

    private ProjectLanguageIndexService createIndexService() {
        LanguageSupport support = new JavaLanguageSupport();
        ProjectLanguageIndexService indexService = new ProjectLanguageIndexService();
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
}
