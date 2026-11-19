package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class GreenNodeCancellationTest {
    @Test
    public void constructorStopsWidthCalculationBeforeReadingAllChildren() {
        var reads = new AtomicInteger();
        List<GreenElement> children = observedChildren(reads);
        var failure = new CancellationException("cancel width calculation");

        assertSame(failure, assertThrows(CancellationException.class,
            () -> new GreenNode(SyntaxKind.NODE, children, () -> {
                if (reads.get() >= 32)
                    throw failure;
            })));

        assertTrue(reads.get() >= 32, "Cancellation must occur during width calculation");
        assertTrue(reads.get() < children.size(), "Cancellation must stop before all children are visited");
        assertEquals(128, new GreenNode(SyntaxKind.NODE, children).width());
    }

    @Test
    public void factoryForwardsCancellationIntoWidthCalculation() {
        var reads = new AtomicInteger();
        List<GreenElement> children = observedChildren(reads);
        var failure = new CancellationException("cancel factory node assembly");

        assertSame(failure, assertThrows(CancellationException.class,
            () -> SyntaxInternalFactory.greenNode(SyntaxKind.NODE, children, () -> {
                if (reads.get() >= 32)
                    throw failure;
            })));

        assertTrue(reads.get() >= 32);
        assertTrue(reads.get() < children.size());
    }

    @Test
    public void factoryChecksPreCancelledRequestsEvenForEmptyNodes() {
        var source = new CancellationTokenSource();
        source.cancel();

        assertThrows(CancellationException.class,
            () -> SyntaxInternalFactory.greenNode(SyntaxKind.NODE, List.of(),
                source.token()::throwIfCancellationRequested));
    }

    @Test
    public void deadlineExpiryStopsWidthCalculation() {
        var clock = new AtomicLong();
        Deadline deadline = Deadline.after(Duration.ofNanos(32), clock::getAndIncrement);
        List<GreenToken> children = Collections.nCopies(128, new GreenToken(SyntaxKind.TOKEN, "x"));

        assertThrows(DeadlineExceededException.class,
            () -> SyntaxInternalFactory.greenNode(SyntaxKind.NODE, children, () -> {
                if (deadline.isExpired())
                    throw new DeadlineExceededException();
            }));
    }

    @Test
    public void activeAssemblyPreservesWidthsCopiesChildrenAndLeavesControlsBehind() {
        var children = new ArrayList<GreenElement>(List.of(
            new GreenToken(SyntaxKind.TOKEN, "ab"),
            new GreenToken(SyntaxKind.TOKEN, "cde")));
        var checks = new AtomicInteger();
        GreenNode checked = SyntaxInternalFactory.greenNode(SyntaxKind.NODE, children, checks::incrementAndGet);
        GreenNode legacy = SyntaxInternalFactory.greenNode(SyntaxKind.NODE, children);
        int completedChecks = checks.get();
        children.clear();

        assertTrue(completedChecks > 0);
        assertSame(legacy.kind(), checked.kind());
        assertEquals(5, checked.width());
        assertEquals(legacy.width(), checked.width());
        assertEquals(legacy.children(), checked.children());
        assertThrows(UnsupportedOperationException.class, () -> checked.children().clear());
        assertEquals(completedChecks, checks.get(), "Completed node reads must not invoke request controls");
    }

    private static List<GreenElement> observedChildren(AtomicInteger reads) {
        var child = new GreenToken(SyntaxKind.TOKEN, "x");
        return new AbstractList<>() {
            @Override
            public GreenElement get(int index) {
                if (index < 0 || index >= size())
                    throw new IndexOutOfBoundsException(index);
                reads.incrementAndGet();
                return child;
            }

            @Override
            public int size() {
                return 128;
            }
        };
    }
}
