package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.index.*;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.events.ProjectConfigurationChangedEvent;
import dev.railroadide.railroad.plugin.spi.event.EventListener;
import dev.railroadide.railroad.project.ProjectManager;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class IDESetupStartupTest {
    @TempDir
    public Path root;

    @Test
    public void failedConstructionReleasesAcceptedOwnership() {
        WorkspaceModes.initialize();
        var project = new RailroadProject(root, "Failure", new WritableImage(1, 1));
        var manager = new ProjectManager(() -> {
        }, event -> Railroad.EVENT_BUS.publish(event));
        assertNotNull(DefaultIDEStateService.getInstance());
        var session = new AtomicReference<ProjectLanguageIndexService.Session>();
        assertThrows(IllegalStateException.class, () -> IDESetup.constructProjectView(manager, project, () -> {
            session.set(Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(project));
            throw new IllegalStateException("View construction failed");
        }));
        assertNotNull(session.get());
        assertTrue(session.get().isClosed());
        assertNull(manager.getOpenProject());
        assertTrue(Services.IDE_STATE.getDocumentStore().isEmpty());
    }

    @Test
    public void vetoPreventsRegistrationAndConstruction() {
        var manager = new ProjectManager(() -> {
        }, _ -> {
        });
        var previous = new RailroadProject(root, "Veto", new WritableImage(1, 1)) {
            @Override
            public void close() {
            }
        };
        manager.setCurrentProject(previous);
        var next = new RailroadProject(root.resolve("next"), "Next", new WritableImage(1, 1));
        assertNull(IDESetup.constructProjectView(manager, next, () -> {
            fail("Veto must precede construction");
            return null;
        }));
        assertSame(previous, manager.getOpenProject());
    }

    @Test
    public void eagerWorkerRetainsAcceptedSessionForSourceAndBuildEvents() throws Exception {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
        assertNotNull(DefaultIDEStateService.getInstance());
        root = root.toRealPath();
        Path source = root.resolve("src/main/java/demo/A.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package demo; class A {}");
        var project = new RailroadProject(root, "Startup", new WritableImage(1, 1));
        var manager = new ProjectManager(() -> {
        }, event -> Railroad.EVENT_BUS.publish(event));
        var events = new AtomicInteger();
        EventListener<ProjectConfigurationChangedEvent> listener = event -> {
            if (event.project() == project) {
                events.incrementAndGet();
            }
        };
        Railroad.EVENT_BUS.subscribe(ProjectConfigurationChangedEvent.class, listener);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var unowned = new ProjectLanguageIndexCoordinator(project, null);
            ProjectLanguageIndexCoordinator coordinator = IDESetup.constructProjectView(manager, project, () -> {
                // Eager explorer worker finishes before view construction can return.
                try {
                    return worker.submit(() -> new ProjectLanguageIndexCoordinator(project)).get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            Services.IDE_STATE.getWorkspaceConfiguration().ready().get(10, TimeUnit.SECONDS);
            Files.writeString(source, "package demo; class A { static int VALUE; }");
            assertTrue(Services.PROJECT_LANGUAGE_INDEX_SERVICE.hasIndexer(project, "java"));
            coordinator.handleFileChange(source, StandardWatchEventKinds.ENTRY_MODIFY);
            var context = new DefaultProjectIndexContextResolver(Services.PROJECT_LANGUAGE_INDEX_SERVICE::session)
                .resolve(project);
            JavaProjectSemanticIndex index = Services.PROJECT_LANGUAGE_INDEX_SERVICE.currentTyped(context,
                JavaLanguageSupport.LANGUAGE_ID);
            assertNotNull(index);
            assertEquals(1, index.lookupMember("demo.A", "VALUE").size());
            coordinator.handleFileChange(root.resolve("pom.xml"), StandardWatchEventKinds.ENTRY_MODIFY);
            assertEquals(1, events.get());
            unowned.handleFileChange(root.resolve("pom.xml"), StandardWatchEventKinds.ENTRY_MODIFY);
            assertEquals(1, events.get(), "Missing identity must not attach to the accepted session");
            manager.setCurrentProject(null);
            manager.setCurrentProject(project);
            coordinator.handleFileChange(root.resolve("pom.xml"), StandardWatchEventKinds.ENTRY_MODIFY);
            assertEquals(1, events.get(), "Closed coordinator must never attach to the reopened path");
        } finally {
            manager.setCurrentProject(null);
            Railroad.EVENT_BUS.unsubscribe(ProjectConfigurationChangedEvent.class, listener);
        }
    }
}
