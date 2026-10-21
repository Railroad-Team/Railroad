package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.project.RailroadProject;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectIndexFacetReadinessTest {
    @TempDir
    public Path root;

    @Test
    public void contextResolutionWaitsForPendingFacetDiscovery() throws Exception {
        var project = new RailroadProject(root, "test", new WritableImage(1, 1));
        var discovery = new CompletableFuture<Void>();
        var field = RailroadProject.class.getDeclaredField("facetDiscovery");
        field.setAccessible(true);
        field.set(project, discovery);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var started = new CountDownLatch(1);
            var context = executor.submit(() -> {
                started.countDown();
                return new DefaultProjectIndexContextResolver().resolve(project);
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> context.get(200, TimeUnit.MILLISECONDS));
            } finally {
                discovery.complete(null);
            }
            assertSame(project, context.get(5, TimeUnit.SECONDS).project());
        }
    }
}
