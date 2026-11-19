package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class SstSchedulerTest {
    private static final WorkspaceSnapshot WORKSPACE = new WorkspaceSnapshot("test", Map.of(),
        new WorkspaceConfigurationSnapshot(0, Set.of()), 0);

    @Test
    public void sharedPrioritiesAndFifoOrderApplyAcrossOwners() throws Exception {
        try (var scheduler = new SstScheduler(1, 16)) {
            var first = scheduler.newScope();
            var second = scheduler.newScope();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var blocker = submit(first, "blocker", RequestPriority.BACKGROUND_INDEXING, () -> {
                entered.countDown();
                await(release);
                return null;
            });
            await(entered);
            List<String> order = new ArrayList<>();
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (RequestPriority priority : List.of(RequestPriority.BACKGROUND_INDEXING,
                RequestPriority.OPEN_DOCUMENT_ANALYSIS, RequestPriority.VISIBLE_DIAGNOSTICS,
                RequestPriority.NAVIGATION, RequestPriority.SIGNATURE_HELP, RequestPriority.COMPLETION)) {
                futures.add(submit(first, priority, priority, () -> order.add(priority.name() + "1")));
                futures.add(submit(second, priority, priority, () -> order.add(priority.name() + "2")));
            }
            release.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            assertEquals(List.of("COMPLETION1", "COMPLETION2", "SIGNATURE_HELP1", "SIGNATURE_HELP2",
                "NAVIGATION1", "NAVIGATION2", "VISIBLE_DIAGNOSTICS1", "VISIBLE_DIAGNOSTICS2", "OPEN_DOCUMENT_ANALYSIS1",
                "OPEN_DOCUMENT_ANALYSIS2", "BACKGROUND_INDEXING1", "BACKGROUND_INDEXING2"), order);
        }
    }

    @Test
    public void supersessionCancelsAndInterruptsRunningWork() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var cancellation = new CancellationTokenSource();
            var context = context(cancellation, Deadline.none());
            var entered = new CountDownLatch(1);
            var interrupted = new CountDownLatch(1);
            var old = scope.submit("completion", RequestPriority.COMPLETION, context, cancellation, () -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                }
                return "obsolete";
            });
            await(entered);
            var replacement = submit(scope, "completion", RequestPriority.COMPLETION, () -> "current");
            await(interrupted);
            assertTrue(cancellation.token().isCancellationRequested());
            assertThrows(CancellationException.class, old::join);
            assertEquals("current", replacement.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void queuedSupersessionReclaimsCapacityWithoutInvokingOldProvider() throws Exception {
        try (var scheduler = new SstScheduler(1, 1)) {
            var scope = scheduler.newScope();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            submit(scope, "blocker", RequestPriority.COMPLETION, () -> {
                entered.countDown();
                await(release);
                return null;
            });
            await(entered);
            var invoked = new AtomicBoolean();
            var old = submit(scope, "same", RequestPriority.COMPLETION, () -> invoked.getAndSet(true));
            CompletableFuture<Void> pending = scope.completion();
            var replacement = submit(scope, "same", RequestPriority.COMPLETION, () -> "new");
            assertTrue(old.isCancelled());
            var rejected = submit(scope, "overflow", RequestPriority.COMPLETION, () -> "unexpected");
            assertInstanceOf(RejectedExecutionException.class,
                assertThrows(ExecutionException.class, () -> rejected.get(5, TimeUnit.SECONDS)).getCause());
            release.countDown();
            assertEquals("new", replacement.get(5, TimeUnit.SECONDS));
            pending.get(5, TimeUnit.SECONDS);
            assertFalse(invoked.get());
        }
    }

    @Test
    public void closingOneOwnerDoesNotStopOtherOwners() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var other = scheduler.newScope();
            var entered = new CountDownLatch(1);
            var cancellation = new CancellationTokenSource();
            var running = scope.submit("running", RequestPriority.COMPLETION,
                context(cancellation, Deadline.none()), cancellation, () -> {
                    entered.countDown();
                    await(new CountDownLatch(1));
                    return null;
                });
            await(entered);
            scope.close();
            assertTrue(cancellation.token().isCancellationRequested());
            assertTrue(running.isCancelled());
            assertEquals("alive", submit(other, "running", RequestPriority.COMPLETION, () -> "alive")
                .get(5, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, assertThrows(ExecutionException.class,
                () -> submit(scope, "closed", RequestPriority.COMPLETION, () -> null).get()).getCause());
        }
    }

    @Test
    public void cancellationOfReturnedFutureStopsItsOperation() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var entered = new CountDownLatch(1);
            var stopped = new CountDownLatch(1);
            var cancellation = new CancellationTokenSource();
            var future = scope.submit("running", RequestPriority.COMPLETION,
                context(cancellation, Deadline.none()), cancellation, () -> {
                    entered.countDown();
                    try {
                        await(new CountDownLatch(1));
                    } finally {
                        stopped.countDown();
                    }
                    return null;
                });
            await(entered);
            assertTrue(future.cancel(true));
            await(stopped);
            assertTrue(cancellation.token().isCancellationRequested());
        }
    }

    @Test
    public void expiredRequestNeverEntersTheProviderAndFailuresDoNotKillWorkers() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var cancellation = new CancellationTokenSource();
            var invoked = new AtomicBoolean();
            var expired = scope.submit("expired", RequestPriority.COMPLETION,
                context(cancellation, Deadline.after(Duration.ZERO)), cancellation, () -> invoked.getAndSet(true));
            assertInstanceOf(DeadlineExceededException.class,
                assertThrows(CancellationException.class, expired::join).getCause());
            assertFalse(invoked.get());
            var failure = submit(scope, "failure", RequestPriority.COMPLETION, () -> {
                throw new IllegalStateException("broken provider");
            });
            assertInstanceOf(IllegalStateException.class,
                assertThrows(ExecutionException.class, () -> failure.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals("healthy", submit(scope, "healthy", RequestPriority.COMPLETION, () -> "healthy")
                .get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void schedulerCloseCancelsQueuedAndRunningRequestsAndRejectsScopes() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var entered = new CountDownLatch(1);
            var running = submit(scope, "running", RequestPriority.COMPLETION, () -> {
                entered.countDown();
                await(new CountDownLatch(1));
                return null;
            });
            await(entered);
            var queued = submit(scope, "queued", RequestPriority.COMPLETION, () -> null);
            scheduler.close();
            assertTrue(running.isCancelled());
            assertTrue(queued.isCancelled());
            assertThrows(RejectedExecutionException.class, scheduler::newScope);
        }
    }

    @Test
    public void completionBarrierIncludesSynchronousPublicationDispatch() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var releaseProvider = new CountDownLatch(1);
            var enteredCallback = new CountDownLatch(1);
            var releaseCallback = new CountDownLatch(1);
            var future = submit(scope, "request", RequestPriority.COMPLETION, () -> {
                await(releaseProvider);
                return "result";
            });
            future.thenAccept(_ -> {
                enteredCallback.countDown();
                await(releaseCallback);
            });
            CompletableFuture<Void> barrier = scope.completion();
            releaseProvider.countDown();
            await(enteredCallback);
            assertFalse(barrier.isDone());
            releaseCallback.countDown();
            barrier.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void supersessionAndClosureCannotRevokeAnOutcomeAlreadyBeingDelivered() throws Exception {
        try (var scheduler = new SstScheduler(1, 4)) {
            var scope = scheduler.newScope();
            var releaseProvider = new CountDownLatch(1);
            var enteredCallback = new CountDownLatch(1);
            var releaseCallback = new CountDownLatch(1);
            var cancellation = new CancellationTokenSource();
            var completed = scope.submit("request", RequestPriority.COMPLETION,
                context(cancellation, Deadline.none()), cancellation, () -> {
                    await(releaseProvider);
                    return "committed";
                });
            completed.thenAccept(_ -> {
                enteredCallback.countDown();
                await(releaseCallback);
            });
            releaseProvider.countDown();
            try {
                await(enteredCallback);
                var replacement = submit(scope, "request", RequestPriority.COMPLETION, () -> "new");
                assertEquals("committed", completed.get(5, TimeUnit.SECONDS));
                assertFalse(cancellation.token().isCancellationRequested());
                scope.close();
                assertTrue(replacement.isCancelled());
                assertEquals("committed", completed.get(5, TimeUnit.SECONDS));
                assertFalse(cancellation.token().isCancellationRequested());
            } finally {
                releaseCallback.countDown();
            }
        }
    }

    @Test
    public void blockedBackgroundWorkCannotConsumeTheInteractiveReservation() throws Exception {
        try (var scheduler = new SstScheduler(2, 4)) {
            var scope = scheduler.newScope();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var background = submit(scope, "background", RequestPriority.BACKGROUND_INDEXING, () -> {
                entered.countDown();
                await(release);
                return null;
            });
            await(entered);
            var secondBackground = submit(scope, "another", RequestPriority.BACKGROUND_INDEXING, () -> null);
            assertEquals("interactive", submit(scope, "completion", RequestPriority.COMPLETION, () -> "interactive")
                .get(5, TimeUnit.SECONDS));
            assertFalse(secondBackground.isDone());
            release.countDown();
            background.get(5, TimeUnit.SECONDS);
            secondBackground.get(5, TimeUnit.SECONDS);
        }
    }

    private static <T> CompletableFuture<T> submit(
        SstScheduler.Scope scope,
        Object key,
        RequestPriority priority,
        Supplier<T> operation
    ) {
        var cancellation = new CancellationTokenSource();
        return scope.submit(key, priority, context(cancellation, Deadline.none()), cancellation, operation);
    }

    private static WorkspaceRequestContext context(CancellationTokenSource source, Deadline deadline) {
        return new WorkspaceRequestContext(WORKSPACE, source.token(), deadline);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Operation did not reach the expected boundary");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Test operation interrupted");
        }
    }
}
