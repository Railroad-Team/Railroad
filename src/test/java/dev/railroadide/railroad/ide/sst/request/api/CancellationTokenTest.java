package dev.railroadide.railroad.ide.sst.request.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CancellationTokenTest {
    @Test
    public void freshSourceIsUncancelled() {
        var source = new CancellationTokenSource();

        assertFalse(source.token().isCancellationRequested());
        assertDoesNotThrow(source.token()::throwIfCancellationRequested);
    }

    @Test
    public void previouslyObtainedTokenObservesCancellationPermanently() {
        var source = new CancellationTokenSource();
        CancellationToken token = source.token();

        assertTrue(source.cancel());
        assertTrue(token.isCancellationRequested());
        assertThrows(CancellationException.class, token::throwIfCancellationRequested);
        assertTrue(token.isCancellationRequested());
        assertThrows(CancellationException.class, token::throwIfCancellationRequested);
    }

    @Test
    public void tokenIdentityIsStableBeforeAndAfterCancellation() {
        var source = new CancellationTokenSource();
        CancellationToken token = source.token();

        assertSame(token, source.token());
        source.cancel();
        assertSame(token, source.token());
    }

    @Test
    public void linkedSourcesKeepStableTokensAndObserveParentCancellationWithoutCancellingSiblings() {
        var lifetime = new CancellationTokenSource();
        var request = new CancellationTokenSource(lifetime.token());
        var sibling = new CancellationTokenSource(lifetime.token());
        CancellationToken requestToken = request.token();
        CancellationToken siblingToken = sibling.token();
        assertSame(requestToken, request.token());
        assertFalse(requestToken.isCancellationRequested());

        assertTrue(request.cancel());
        assertTrue(requestToken.isCancellationRequested());
        assertFalse(lifetime.token().isCancellationRequested());
        assertFalse(siblingToken.isCancellationRequested());

        lifetime.cancel();
        assertTrue(siblingToken.isCancellationRequested());
        assertSame(siblingToken, sibling.token());
        assertSame(requestToken, request.token());
        assertThrows(CancellationException.class, siblingToken::throwIfCancellationRequested);
        assertTrue(sibling.cancel());
        assertFalse(sibling.cancel());
    }

    @Test
    public void linkedSourceImmediatelyObservesAnAlreadyCancelledParent() {
        var lifetime = new CancellationTokenSource();
        lifetime.cancel();
        var request = new CancellationTokenSource(lifetime.token());
        assertTrue(request.token().isCancellationRequested());
        assertThrows(CancellationException.class, request.token()::throwIfCancellationRequested);
        assertThrows(NullPointerException.class, () -> new CancellationTokenSource(null));
    }

    @Test
    public void onlyTheFirstCancellationReportsSuccess() {
        var source = new CancellationTokenSource();

        assertTrue(source.cancel());
        assertFalse(source.cancel());
        assertFalse(source.cancel());
        assertTrue(source.token().isCancellationRequested());
    }

    @Test
    public void sourcesHaveIndependentCancellationState() {
        var first = new CancellationTokenSource();
        var second = new CancellationTokenSource();

        first.cancel();

        assertTrue(first.token().isCancellationRequested());
        assertFalse(second.token().isCancellationRequested());
        assertDoesNotThrow(second.token()::throwIfCancellationRequested);
        assertTrue(second.cancel());
    }

    @Test
    public void noneIsSharedAndRemainsUncancelled() {
        CancellationToken token = CancellationToken.none();
        var source = new CancellationTokenSource();

        assertSame(token, CancellationToken.none());
        assertFalse(token.isCancellationRequested());
        assertDoesNotThrow(token::throwIfCancellationRequested);

        source.cancel();

        assertSame(token, CancellationToken.none());
        assertFalse(token.isCancellationRequested());
        assertDoesNotThrow(token::throwIfCancellationRequested);
    }

    @Test
    public void cancellationChecksPreserveThreadInterruptStatus() throws Exception {
        var source = new CancellationTokenSource();

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> result = executor.submit(() -> {
                Thread.currentThread().interrupt();
                try {
                    assertFalse(source.token().isCancellationRequested());
                    assertDoesNotThrow(source.token()::throwIfCancellationRequested);
                    assertTrue(Thread.currentThread().isInterrupted());

                    assertFalse(CancellationToken.none().isCancellationRequested());
                    assertDoesNotThrow(CancellationToken.none()::throwIfCancellationRequested);
                    assertTrue(Thread.currentThread().isInterrupted());

                    assertTrue(source.cancel());
                    assertThrows(CancellationException.class, source.token()::throwIfCancellationRequested);
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            });

            result.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void workerObservesCancellationWithoutBeingInterruptedOrStoppedByTheSource() throws Exception {
        var source = new CancellationTokenSource();
        CancellationToken token = source.token();
        var ready = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> result = executor.submit(() -> {
                assertFalse(token.isCancellationRequested());
                ready.countDown();
                assertTrue(releaseWorker.await(10, TimeUnit.SECONDS), "Owner did not release the worker");
                assertFalse(Thread.currentThread().isInterrupted());
                assertTrue(token.isCancellationRequested());
                assertThrows(CancellationException.class, token::throwIfCancellationRequested);
                return null;
            });

            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS), "Worker did not become ready");
                assertTrue(source.cancel());
                assertFalse(result.isDone(), "Cancellation must leave the worker in control of stopping");
            } finally {
                releaseWorker.countDown();
            }

            result.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void racingCancellationCallsHaveExactlyOneWinner() throws Exception {
        int attempts = 10_000;
        var sources = new ArrayList<CancellationTokenSource>(attempts);
        for (int attempt = 0; attempt < attempts; attempt++) {
            sources.add(new CancellationTokenSource());
        }

        // Repeated fresh sources exercise competing calls without assuming a particular
        // thread schedule. A passing run does not prove that every interleaving is safe.
        var start = new Phaser(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<boolean[]>> workers = new ArrayList<>();
            for (int worker = 0; worker < 2; worker++) {
                workers.add(executor.submit(() -> {
                    var results = new boolean[attempts];
                    for (int attempt = 0; attempt < attempts; attempt++) {
                        start.awaitAdvanceInterruptibly(start.arrive(), 10, TimeUnit.SECONDS);
                        results[attempt] = sources.get(attempt).cancel();
                    }
                    return results;
                }));
            }

            boolean[] first = workers.get(0).get(30, TimeUnit.SECONDS);
            boolean[] second = workers.get(1).get(30, TimeUnit.SECONDS);
            for (int attempt = 0; attempt < attempts; attempt++) {
                int winners = (first[attempt] ? 1 : 0) + (second[attempt] ? 1 : 0);
                assertEquals(1, winners, "Expected exactly one successful cancellation in attempt " + attempt);
                assertTrue(sources.get(attempt).token().isCancellationRequested());
                assertFalse(sources.get(attempt).cancel());
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Cancellation workers did not stop");
        }
    }
}
