package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.WorkspaceModes;
import javafx.application.Platform;
import dev.railroadide.railroad.ide.sst.request.api.SstScheduler;

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

    public static void awaitRequests(SstScheduler.Scope scope) throws InterruptedException {
        try {
            scope.completion().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("Earlier requests did not finish", exception);
        }
    }

}
