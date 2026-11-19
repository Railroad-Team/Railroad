package dev.railroadide.railroad.ide.sst.request.internal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ActivityCheckpointTest {
    @Test
    public void shortLoopsShareOneWorkBudget() {
        var checks = new AtomicInteger();
        Runnable callback = new ActivityCheckpoint(checks::incrementAndGet, 4);

        for (int call = 0; call < 4; call++) {
            ActivityCheckpoint.poll(callback);
        }
        assertEquals(1, checks.get());

        ActivityCheckpoint.poll(callback);
        assertEquals(2, checks.get());
    }

    @Test
    public void immediateChecksCannotBeSkippedByAnUnspentBudget() {
        var failure = new CancellationException("cancel while waiting");
        var checks = new AtomicInteger();
        var checkpoint = new ActivityCheckpoint(() -> {
            if (checks.incrementAndGet() > 1)
                throw failure;
        }, 256);

        checkpoint.run();

        assertSame(failure, assertThrows(CancellationException.class, checkpoint::run));
    }

    @Test
    public void inactivityIsObservedWithinTheConfiguredPollingInterval() {
        var active = new AtomicInteger(1);
        var failure = new CancellationException("cancel operation");
        var checkpoint = new ActivityCheckpoint(() -> {
            if (active.get() == 0)
                throw failure;
        }, 4);
        ActivityCheckpoint.poll(checkpoint);
        active.set(0);

        for (int skipped = 0; skipped < 3; skipped++) {
            assertDoesNotThrow(() -> ActivityCheckpoint.poll(checkpoint));
        }
        assertSame(failure, assertThrows(CancellationException.class, () -> ActivityCheckpoint.poll(checkpoint)));
    }

    @Test
    public void ordinaryCallbacksRetainImmediateBehavior() {
        var checks = new AtomicInteger();
        Runnable callback = checks::incrementAndGet;

        for (int call = 0; call < 4; call++) {
            ActivityCheckpoint.poll(callback);
        }

        assertEquals(4, checks.get());
    }

    @Test
    public void failedChecksArePropagatedWithoutGrantingMoreWork() {
        var failure = new CancellationException("cancel operation");
        var checkpoint = new ActivityCheckpoint(() -> {
            throw failure;
        }, 256);

        assertSame(failure, assertThrows(CancellationException.class, () -> ActivityCheckpoint.poll(checkpoint)));
        assertSame(failure, assertThrows(CancellationException.class, () -> ActivityCheckpoint.poll(checkpoint)));
    }

    @Test
    public void invalidCheckingBudgetsAreRejected() {
        assertThrows(NullPointerException.class, () -> new ActivityCheckpoint(null));
        assertThrows(IllegalArgumentException.class, () -> new ActivityCheckpoint(() -> {
        }, 0));
        assertThrows(IllegalArgumentException.class, () -> new ActivityCheckpoint(() -> {
        }, -1));
    }
}
