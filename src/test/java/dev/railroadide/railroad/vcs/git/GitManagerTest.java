package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.vcs.git.execution.GitProcessRunner;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

public class GitManagerTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void stoppingDuringDetectionKeepsAutoRefreshOffUntilTheNextDetection() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var client = new GitClient(new GitProcessRunner(directory.resolve("missing-git"))) {
            @Override
            public Optional<GitRepository> detectRepository(Path path) {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of(new GitRepository(path));
            }
        };
        var scheduled = new AtomicInteger();
        var executor = new ScheduledThreadPoolExecutor(1) {
            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command,
                long initialDelay,
                long period,
                TimeUnit unit
            ) {
                scheduled.incrementAndGet();
                return super.scheduleAtFixedRate(() -> {
                }, initialDelay, period, unit);
            }
        };
        var manager = new GitManager(new RailroadProject(directory, "Git", new WritableImage(1, 1)), client,
            executor);
        try {
            manager.detectRepository();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            manager.stop();
            release.countDown();
            awaitOnFx(() -> manager.getRepositoryState() == GitRepositoryState.AVAILABLE);
            assertEquals(0, scheduled.get());

            manager.detectRepository();
            awaitOnFx(() -> scheduled.get() == 1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitOnFx(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var checked = new CountDownLatch(1);
            var satisfied = new boolean[1];
            Platform.runLater(() -> {
                satisfied[0] = condition.getAsBoolean();
                checked.countDown();
            });
            assertTrue(checked.await(5, TimeUnit.SECONDS));
            if (satisfied[0])
                return;
            Thread.onSpinWait();
        }
        fail("Condition was not met on the JavaFX thread");
    }
}
