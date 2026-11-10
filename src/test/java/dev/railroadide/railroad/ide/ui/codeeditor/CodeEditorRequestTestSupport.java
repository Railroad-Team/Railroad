package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.WorkspaceModes;
import javafx.application.Platform;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public final class CodeEditorRequestTestSupport {
    private CodeEditorRequestTestSupport() {
    }

    public static void startFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    public static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    public static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Test did not release the coordinated operation");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Test operation interrupted");
        }
    }

    public static void awaitWorkers(ExecutorService worker, int blockedRequests) throws InterruptedException {
        var executor = (ThreadPoolExecutor) worker;
        int available = executor.getCorePoolSize() - blockedRequests;
        var ready = new CountDownLatch(available);
        var release = new CountDownLatch(1);
        try {
            // Occupying the remaining workers proves earlier tasks and their synchronous
            // future continuations have finished queuing their JavaFX callbacks.
            for (int index = 0; index < available; index++) {
                executor.execute(() -> {
                    ready.countDown();
                    await(release);
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Earlier worker tasks did not finish");
        } finally {
            release.countDown();
        }
    }

}
