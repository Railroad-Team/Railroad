package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ProviderExecutorTest {
    private static final Duration BUDGET = Duration.ofSeconds(5);

    private static WorkspaceRequestContext request(CancellationToken cancellation) {
        return new WorkspaceRequestContext(new WorkspaceSnapshot("provider-test", Map.of(),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 7), cancellation, Deadline.none());
    }

    @Test
    public void failedProviderDoesNotPoisonHealthyProvidersAndHealthHasNoFailureObject() {
        try (var executor = new ProviderExecutor(2, 1, 4, Duration.ZERO)) {
            var request = request(CancellationToken.none());
            assertThrows(IllegalStateException.class, () -> executor.execute("broken", request, BUDGET, () -> {
                throw new IllegalStateException("failure");
            }));
            assertEquals("healthy", executor.execute("healthy", request, BUDGET, () -> "healthy"));
            var broken = executor.health().stream().filter(item -> item.providerId().equals("broken")).findFirst()
                .orElseThrow();
            assertEquals(1, broken.failed());
            assertEquals(IllegalStateException.class.getName(), broken.lastFailureType());
            assertEquals("provider-test", broken.lastProjectId());
            assertEquals(7, broken.lastWorkspaceRevision());
            assertTrue(executor.diagnostics().stream().anyMatch(item -> item.providerId().equals("broken")));
        }
    }

    @Test
    public void timeoutDiscardsLateResultAndKeepsUncooperativeWorkerWithinItsAllowance() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        try (var executor = new ProviderExecutor(2, 1, 4, Duration.ZERO)) {
            var request = request(CancellationToken.none());
            try {
                assertThrows(DeadlineExceededException.class, () -> executor.execute("slow", request,
                    Duration.ofMillis(150), () -> {
                        entered.countDown();
                        awaitIgnoringInterrupt(release);
                        exited.countDown();
                        return "late";
                    }));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                var health = executor.health().getFirst();
                assertEquals(1, health.timedOut());
                assertEquals(1, health.active());
                assertEquals(0, health.succeeded());
                assertThrows(RejectedExecutionException.class,
                    () -> executor.execute("slow", request, BUDGET, () -> "should not run"));
                assertEquals("healthy", executor.execute("healthy", request, BUDGET, () -> "healthy"));
            } finally {
                release.countDown();
            }
            assertTrue(exited.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cancellationReturnsWithoutWaitingForProviderAndIsNotAProviderFailure() throws Exception {
        var source = new CancellationTokenSource();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CompletableFuture<Throwable>();
        try (var executor = new ProviderExecutor(2, 1, 4, BUDGET)) {
            Thread caller = Thread.ofVirtual().start(() -> {
                try {
                    executor.execute("cancelled", request(source.token()), BUDGET, () -> {
                        entered.countDown();
                        awaitIgnoringInterrupt(release);
                        return "late";
                    });
                    finished.complete(null);
                } catch (Throwable failure) {
                    finished.complete(failure);
                }
            });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                source.cancel();
                assertInstanceOf(CancellationException.class, finished.get(2, TimeUnit.SECONDS));
                var health = executor.health().getFirst();
                assertEquals(1, health.cancelled());
                assertEquals(0, health.failed());
                assertEquals(1, health.active());
            } finally {
                release.countDown();
                caller.join(2000);
            }
        }
    }

    @Test
    public void childContextRetainsInputsAndObservesProviderDeadline() {
        try (var executor = new ProviderExecutor(2, 1, 4, BUDGET)) {
            var request = request(CancellationToken.none());
            var observed = new AtomicReference<RequestContext>();
            assertThrows(DeadlineExceededException.class, () -> executor.executeWithContext("cooperative", request,
                Duration.ofMillis(100), child -> {
                    observed.set(child);
                    while (true) {
                        child.checkActive();
                    }
                }));
            assertNotSame(request, observed.get());
            assertSame(request.workspaceSnapshot(), observed.get().workspaceSnapshot());
            assertTrue(observed.get().deadline().isExpired());
            assertDoesNotThrow(request::checkActive);
        }
    }

    @Test
    public void binaryProviderReceivesCapturedIdentityAndParentDeadlineClock() {
        var id = DocumentId.create();
        var document = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "bytecode", new byte[]{1, 2});
        var workspace = new WorkspaceSnapshot("binary-test", Map.of(id, document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 4);
        var clock = new AtomicLong();
        var parent = new FeatureRequestContext(workspace, document, CancellationToken.none(),
            Deadline.after(Duration.ofNanos(10), clock::get));
        try (var executor = new ProviderExecutor(2, 1, 4, BUDGET)) {
            assertThrows(DeadlineExceededException.class,
                () -> executor.executeWithContext("bytecode", parent, BUDGET, child -> {
                    assertSame(workspace, child.workspaceSnapshot());
                    assertSame(document, ((FeatureRequestContext) child).documentSnapshot());
                    clock.set(10);
                    child.checkActive();
                    return "unreachable";
                }));
            assertEquals(1, executor.health().getFirst().timedOut());
        }
    }

    @Test
    public void cancellationFromDeadlineAdaptersRetainsTimeoutMetrics() {
        var clock = new AtomicLong();
        var workspace = request(CancellationToken.none()).workspaceSnapshot();
        var context = new WorkspaceRequestContext(workspace, CancellationToken.none(),
            Deadline.after(Duration.ofNanos(10), clock::get));
        try (var executor = new ProviderExecutor(2, 1, 4, BUDGET)) {
            assertThrows(DeadlineExceededException.class, () -> executor.execute("adapted-index", context, BUDGET,
                () -> {
                    clock.set(10);
                    throw new CancellationException("Index token observed an expired deadline");
                }));
            ProviderHealth health = executor.health().getFirst();
            assertEquals(1, health.timedOut());
            assertEquals(0, health.cancelled());
        }
    }

    @Test
    public void completedColdWarmAndChangedRequestsHaveBoundedAggregateMeasurements() {
        try (var executor = new ProviderExecutor(2, 1, 2, Duration.ZERO)) {
            var first = request(CancellationToken.none());
            var changed = new WorkspaceRequestContext(new WorkspaceSnapshot("provider-test", Map.of(),
                first.workspaceSnapshot().configuration(), 8), CancellationToken.none(), Deadline.none());
            assertEquals(1, executor.execute("analysis", first, BUDGET, () -> 1));
            assertEquals(2, executor.execute("analysis", first, BUDGET, () -> 2));
            assertEquals(3, executor.execute("analysis", changed, BUDGET, () -> 3));
            var health = executor.health().getFirst();
            assertEquals(3, health.succeeded());
            assertEquals(3, health.slow());
            assertEquals(0, health.active());
            assertEquals(8, health.lastWorkspaceRevision());
            assertTrue(health.totalQueueNanos() >= 0);
            assertTrue(health.totalExecutionNanos() > 0);
            assertTrue(health.maximumExecutionNanos() <= health.totalExecutionNanos());
            for (int index = 0; index < 20; index++) {
                executor.execute("provider-" + index, first, BUDGET, () -> null);
            }
            assertEquals(2, executor.health().size());
        }
    }

    @Test
    public void closeCancelsActiveCallsWithoutWaitingAndRejectsReuse() throws Exception {
        var executor = new ProviderExecutor(1, 1, 1, BUDGET);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CompletableFuture<Throwable>();
        var request = request(CancellationToken.none());
        Thread caller = Thread.ofVirtual().start(() -> {
            try {
                executor.execute("closing", request, BUDGET, () -> {
                    entered.countDown();
                    awaitIgnoringInterrupt(release);
                    return null;
                });
                finished.complete(null);
            } catch (Throwable failure) {
                finished.complete(failure);
            }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            executor.close();
            assertInstanceOf(CancellationException.class, finished.get(2, TimeUnit.SECONDS));
            assertTrue(executor.health().isEmpty());
            assertThrows(RejectedExecutionException.class, () -> executor.execute("closed", request, BUDGET, () -> 1));
        } finally {
            release.countDown();
            executor.close();
            caller.join(2000);
        }
    }

    @Test
    public void completedExecutionRemainsPinnedUntilItsResponseAndCloseRejectsThatResponse() throws Exception {
        var caller = new AtomicReference<Thread>();
        var returned = new AtomicBoolean();
        var gated = new AtomicBoolean();
        var responseGate = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var outcome = new CompletableFuture<Throwable>();
        try (var executor = new ProviderExecutor(2, 1, 1, BUDGET)) {
            var request = request(() -> {
                if (Thread.currentThread() == caller.get() && returned.get() && !gated.get()
                    && executor.health().getFirst().active() == 0 && gated.compareAndSet(false, true)) {
                    responseGate.countDown();
                    awaitIgnoringInterrupt(release);
                }
                return false;
            });
            Thread thread = Thread.ofVirtual().start(() -> {
                caller.set(Thread.currentThread());
                try {
                    executor.execute("finishing", request, BUDGET, () -> {
                        returned.set(true);
                        return "finished";
                    });
                    outcome.complete(null);
                } catch (Throwable failure) {
                    outcome.complete(failure);
                }
            });
            try {
                assertTrue(responseGate.await(2, TimeUnit.SECONDS));
                assertThrows(RejectedExecutionException.class,
                    () -> executor.execute("replacement", request(CancellationToken.none()), BUDGET, () -> null));
                executor.close();
                release.countDown();
                assertInstanceOf(CancellationException.class, outcome.get(2, TimeUnit.SECONDS));
                assertTrue(executor.health().isEmpty());
            } finally {
                release.countDown();
                thread.join(2000);
            }
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException _) {
                // Models an uncooperative plugin; the test always releases it explicitly.
            }
        }
    }
}
