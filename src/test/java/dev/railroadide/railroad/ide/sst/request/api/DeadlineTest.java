package dev.railroadide.railroad.ide.sst.request.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class DeadlineTest {
    @Test
    public void unlimitedDeadlineIsShared() {
        assertSame(Deadline.none(), Deadline.none());
    }

    @Test
    public void unlimitedDeadlineNeverExpiresAndHasNoFiniteRemainingDuration() {
        Deadline deadline = Deadline.none();

        assertFalse(deadline.isExpired());
        assertEquals(Optional.empty(), deadline.remaining());
    }

    @Test
    public void finiteBudgetStartsAtCreationAndDecreasesWithoutRestarting() {
        var clock = new AtomicLong(1_000);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), clock::get);

        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(50)), deadline.remaining());

        clock.set(1_020);
        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(30)), deadline.remaining());
        assertEquals(Optional.of(Duration.ofNanos(30)), deadline.remaining());

        clock.set(1_049);
        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(1)), deadline.remaining());
    }

    @Test
    public void exactBoundaryIsExpiredWithAPresentZeroRemainingDuration() {
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), clock::get);

        clock.set(150);

        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void expiredFiniteBudgetKeepsReturningZeroAsTimeAdvances() {
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), clock::get);

        clock.set(151);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());

        clock.set(10_000);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void zeroBudgetIsImmediatelyExpired() {
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ZERO, clock::get);

        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void defaultTimeSourceAlsoAcceptsAnImmediatelyExpiredBudget() {
        Deadline deadline = Deadline.after(Duration.ZERO);

        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void negativeCounterOriginsCanCrossZeroWithoutChangingTheBudget() {
        var clock = new AtomicLong(-25);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), clock::get);

        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(50)), deadline.remaining());

        clock.set(24);
        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(1)), deadline.remaining());

        clock.set(25);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void budgetDoesNotExpireEarlyWhenAnAbsoluteEndCounterWouldOverflow() {
        var clock = new AtomicLong(Long.MAX_VALUE - 5);
        Deadline deadline = Deadline.after(Duration.ofNanos(10), clock::get);

        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(10)), deadline.remaining());

        clock.set(Long.MAX_VALUE);
        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(5)), deadline.remaining());
    }

    @Test
    public void elapsedTimeRemainsCorrectAfterTheCounterWraps() {
        var clock = new AtomicLong(Long.MAX_VALUE - 5);
        Deadline deadline = Deadline.after(Duration.ofNanos(10), clock::get);

        // These counter values are nine and ten nanoseconds after creation.
        clock.set(Long.MIN_VALUE + 3);
        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(1)), deadline.remaining());

        clock.set(Long.MIN_VALUE + 4);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());

        clock.set(Long.MIN_VALUE + 5);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void counterWraparoundDoesNotReviveAnAlreadyExpiredDeadline() {
        var clock = new AtomicLong(Long.MAX_VALUE - 20);
        Deadline deadline = Deadline.after(Duration.ofNanos(5), clock::get);

        clock.set(Long.MAX_VALUE - 10);
        assertTrue(deadline.isExpired());

        clock.set(Long.MIN_VALUE);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void largestRepresentableBudgetIsFinite() {
        var clock = new AtomicLong(0);
        Duration budget = Duration.ofNanos(Long.MAX_VALUE);
        Deadline deadline = Deadline.after(budget, clock::get);

        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(budget), deadline.remaining());

        clock.set(Long.MAX_VALUE - 1);
        assertFalse(deadline.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(1)), deadline.remaining());

        clock.set(Long.MAX_VALUE);
        assertTrue(deadline.isExpired());
        assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
    }

    @Test
    public void deadlinesSharingAClockKeepTheirOwnStartingTimes() {
        var clock = new AtomicLong(100);
        Deadline first = Deadline.after(Duration.ofNanos(50), clock::get);

        clock.set(120);
        Deadline second = Deadline.after(Duration.ofNanos(50), clock::get);

        assertEquals(Optional.of(Duration.ofNanos(30)), first.remaining());
        assertEquals(Optional.of(Duration.ofNanos(50)), second.remaining());

        clock.set(150);
        assertTrue(first.isExpired());
        assertFalse(second.isExpired());
        assertEquals(Optional.of(Duration.ofNanos(20)), second.remaining());
    }

    @Test
    public void negativeTimeoutsAreRejectedBeforeNanosecondConversion() {
        var clock = new AtomicLong(100);
        assertThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofNanos(-1), clock::get));
        assertThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class,
            () -> Deadline.after(Duration.ofSeconds(Long.MIN_VALUE), clock::get));
        assertThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofSeconds(Long.MIN_VALUE)));
    }

    @Test
    public void positiveTimeoutsTooLargeForNanosecondsAreRejected() {
        var clock = new AtomicLong(100);
        Duration oversized = Duration.ofNanos(Long.MAX_VALUE).plusNanos(1);

        assertThrows(ArithmeticException.class, () -> Deadline.after(oversized, clock::get));
        assertThrows(ArithmeticException.class, () -> Deadline.after(oversized));
    }

    @Test
    public void factoriesRejectNullArguments() {
        var clock = new AtomicLong(100);

        assertThrows(NullPointerException.class, () -> Deadline.after(null));
        assertThrows(NullPointerException.class, () -> Deadline.after(null, clock::get));
        assertThrows(NullPointerException.class, () -> Deadline.after(Duration.ofNanos(1), null));
    }

    @Test
    public void workersCanShareADeadlineAcrossItsExpirationBoundary() throws Exception {
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), clock::get);
        clock.set(149);
        var checkedBeforeExpiration = new CountDownLatch(4);
        var expired = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                workers.add(executor.submit(() -> {
                    try {
                        assertFalse(deadline.isExpired());
                        assertEquals(Optional.of(Duration.ofNanos(1)), deadline.remaining());
                    } finally {
                        checkedBeforeExpiration.countDown();
                    }
                    assertTrue(expired.await(10, TimeUnit.SECONDS), "Owner did not advance the clock");
                    assertTrue(deadline.isExpired());
                    assertEquals(Optional.of(Duration.ZERO), deadline.remaining());
                    return null;
                }));
            }

            try {
                assertTrue(checkedBeforeExpiration.await(10, TimeUnit.SECONDS), "Workers did not check the deadline");
                clock.set(150);
            } finally {
                expired.countDown();
            }

            for (Future<?> worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
        }
    }
}
