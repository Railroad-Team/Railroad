package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FxBlocker implements AutoCloseable {
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CompletableFuture<Void> finished = new CompletableFuture<>();

    private FxBlocker() {
    }

    public static FxBlocker start(Runnable beforeQueuedPublications) throws InterruptedException {
        var blocker = new FxBlocker();
        Platform.runLater(() -> {
            blocker.entered.countDown();
            try {
                CodeEditorRequestTestSupport.await(blocker.release);
                beforeQueuedPublications.run();
                blocker.finished.complete(null);
            } catch (Throwable failure) {
                blocker.finished.completeExceptionally(failure);
            }
        });
        assertTrue(blocker.entered.await(10, TimeUnit.SECONDS));
        return blocker;
    }

    public void releaseAndAwait() throws Exception {
        release.countDown();
        finished.get(10, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        releaseAndAwait();
    }
}
