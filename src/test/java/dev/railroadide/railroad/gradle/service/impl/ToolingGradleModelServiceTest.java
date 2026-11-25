package dev.railroadide.railroad.gradle.service.impl;

import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.PersistedGradleModelStore;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroadplugin.dto.impl.BasicRailroadProject;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

public class ToolingGradleModelServiceTest {
    private static final Instant IMPORT_STARTED = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    public Path temp;

    private Path root;
    private Project project;
    private PersistedGradleModelStore store;
    private final List<Runnable> submittedImports = new ArrayList<>();
    private final Executor executor = submittedImports::add;

    @BeforeEach
    public void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("build"));
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }");
        Files.setLastModifiedTime(root.resolve("build.gradle"), FileTime.from(IMPORT_STARTED.minusSeconds(60)));
        project = new RailroadProject(root, "Build", new WritableImage(1, 1));
        store = new PersistedGradleModelStore(temp.resolve("models"), "importer");
    }

    @Test
    public void currentPersistedModelIsUsedWithoutImporting() throws IOException {
        GradleBuildModel persisted = model();
        assertTrue(store.save(root, persisted, IMPORT_STARTED));
        var service = new ToolingGradleModelService(project, new StubEnvironment(project), executor, store);

        Optional<GradleBuildModel> cached = service.getCachedModel();
        CompletableFuture<GradleBuildModel> refresh = service.refreshModel(false);

        assertEquals("9.7.0", cached.orElseThrow().gradleVersion());
        assertTrue(refresh.isDone());
        assertSame(cached.orElseThrow(), refresh.join());
        assertTrue(submittedImports.isEmpty());
    }

    @Test
    public void stalePersistedModelIsUsedWhileReimporting() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java-library' }");
        var service = new ToolingGradleModelService(project, new StubEnvironment(project), executor, store);

        Optional<GradleBuildModel> cached = service.getCachedModel();

        assertEquals("9.7.0", cached.orElseThrow().gradleVersion());
        assertEquals(1, submittedImports.size());
        service.getCachedModel();
        assertEquals(1, submittedImports.size());
    }

    @Test
    public void missingPersistedModelImportsOnRefresh() {
        var service = new ToolingGradleModelService(project, new StubEnvironment(project), executor, store);

        assertEquals(Optional.empty(), service.getCachedModel());
        assertTrue(submittedImports.isEmpty());
        assertFalse(service.refreshModel(false).isDone());
        assertEquals(1, submittedImports.size());
    }

    @Test
    public void unreadablePersistedModelFallsBackToImport() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        try (var files = Files.list(temp.resolve("models"))) {
            for (Path snapshot : files.toList()) {
                Files.writeString(snapshot, "not a snapshot");
            }
        }

        var service = new ToolingGradleModelService(project, new StubEnvironment(project), executor, store);

        assertEquals(Optional.empty(), service.getCachedModel());
        assertFalse(service.refreshModel(false).isDone());
    }

    private GradleBuildModel model() {
        return new GradleBuildModel("9.7.0", root, null,
            new BasicRailroadProject("build", null, null, List.of(), null));
    }

    private record StubEnvironment(Project project) implements GradleEnvironment {
        @Override
        public boolean useWrapper() {
            return true;
        }

        @Override
        public Optional<Path> installationPath() {
            return Optional.empty();
        }

        @Override
        public Optional<Path> userHomePath() {
            return Optional.empty();
        }

        @Override
        public Optional<JDK> jvm() {
            return Optional.empty();
        }

        @Override
        public String jvmArgumentsFor(GradleTaskExecutionRequest request, JDK jvm) {
            return "";
        }

        @Override
        public boolean isDaemonEnabled() {
            return true;
        }

        @Override
        public Optional<Long> daemonIdleTimeout() {
            return Optional.empty();
        }
    }
}
