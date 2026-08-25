package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
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
        ProjectIndexContext context = createContext(root);
        var coordinator = new ProjectLanguageIndexCoordinator(
            context, indexService, List.of(new JavaLanguageSupport()));

        coordinator.warmIndexes();

        JavaProjectSemanticIndex index = indexService.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID);
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
        ProjectIndexContext context = createContext(root);
        var coordinator = new ProjectLanguageIndexCoordinator(
            context, indexService, List.of(new JavaLanguageSupport()));
        coordinator.warmIndexes();

        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_MODIFY);

        JavaProjectSemanticIndex updated = indexService.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(updated);
        assertEquals(1, updated.lookupMember("demo.A", "VALUE").size());

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_DELETE);

        JavaProjectSemanticIndex removed = indexService.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(removed);
        assertTrue(removed.lookupQualifiedName("demo.A").isEmpty());
    }

    private ProjectLanguageIndexService createIndexService() {
        LanguageSupport support = new JavaLanguageSupport();
        var indexService = new ProjectLanguageIndexService();
        indexService.registerIndexer(support.createIndexer());
        indexService.registerPersistence(support.createPersistence());
        return indexService;
    }

    private ProjectIndexContext createContext(Path root) {
        Project project = (Project) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{Project.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPath" -> root;
                case "getAlias" -> root.getFileName().toString();
                case "hasFacet" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "TestProject[" + root + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new DefaultProjectIndexContextResolver().resolve(project);
    }

    private Path createProject(String source) throws Exception {
        Path root = tempDir.resolve("project-" + System.nanoTime());
        Path file = root.resolve("src/main/java/demo/A.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return root;
    }
}
