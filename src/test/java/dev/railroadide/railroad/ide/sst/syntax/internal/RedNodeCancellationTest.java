package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RedNodeCancellationTest {
    @Test
    public void interruptedChildBuildCanBeRetriedWithoutPublishingAPartialList() {
        RedNode root = root(128);
        var failure = new CancellationException("cancel child construction");
        var checks = new AtomicInteger();

        assertSame(failure, assertThrows(CancellationException.class, () -> root.children(() -> {
            if (checks.incrementAndGet() >= 8)
                throw failure;
        })));

        List<SyntaxNode> children = root.children();
        assertEquals(128, children.size());
        for (int index = 0; index < children.size(); index++) {
            SyntaxNode child = children.get(index);
            assertEquals(index, child.start());
            assertEquals(index + 1, child.end());
            assertSame(root, child.parent().orElseThrow());
        }
        assertSame(children, root.children(() -> {
        }));
        assertThrows(UnsupportedOperationException.class, children::clear);
        assertDoesNotThrow(() -> SyntaxTreeValidator.validate(root));
    }

    @Test
    public void cancelledCallerCannotBypassItsCheckUsingAWarmCache() {
        RedNode root = root(4);
        List<SyntaxNode> children = root.children();
        var source = new CancellationTokenSource();
        source.cancel();

        assertThrows(CancellationException.class, () -> root.children(source.token()::throwIfCancellationRequested));
        assertSame(children, root.children());
    }

    @Test
    public void waiterRechecksCancellationBeforeReturningAnotherCallersCompletedCache() throws Exception {
        RedNode root = root(4);
        var source = new CancellationTokenSource();
        var firstCheck = new CountDownLatch(1);
        var result = new FutureTask<>(() -> root.children(new ActivityCheckpoint(() -> {
            source.token().throwIfCancellationRequested();
            firstCheck.countDown();
        })));
        Thread waiter = Thread.ofPlatform().unstarted(result);

        try {
            synchronized (root) {
                waiter.start();
                assertTrue(firstCheck.await(10, TimeUnit.SECONDS));
                awaitBlocked(waiter);
                source.cancel();
                // Populate the cache while the cancelled caller is waiting for this monitor.
                root.children();
            }

            ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
        } finally {
            waiter.join(10000);
            assertFalse(waiter.isAlive(), "Child-cache waiter did not finish");
        }
    }

    private static RedNode root(int childCount) {
        return RedFactory.root(GreenNode.root(Collections.nCopies(childCount, new GreenToken(SyntaxKind.TOKEN, "x"))));
    }

    private static void awaitBlocked(Thread thread) {
        long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.getState() != Thread.State.BLOCKED) {
            assertTrue(thread.isAlive(), "Worker finished before waiting for the child-cache monitor");
            assertTrue(System.nanoTime() - timeout < 0, "Worker did not wait for the child-cache monitor");
            Thread.yield();
        }
    }
}
