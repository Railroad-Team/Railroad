package dev.railroadide.railroad.ide.sst.request.internal;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Shares one cooperative checking budget across the hot loops of a synchronous operation.
 * {@link #run()} always checks immediately; {@link #poll(Runnable)} batches checks only
 * when given this wrapper, preserving immediate behavior for ordinary callbacks.
 *
 * <p>
 * Instances are confined to one worker invocation and are not thread-safe. They must
 * never be stored in a completed syntax tree. A work interval bounds checkpoint calls,
 * not elapsed time; blocking operations still require an immediate check after waiting.
 */
@ApiStatus.Internal
public final class ActivityCheckpoint implements Runnable {
    private static final int DEFAULT_INTERVAL = 256;

    private final Runnable activityCheck;
    private final int interval;
    private int remaining;

    /**
     * Checks interruption for synchronous callers that do not supply a request context.
     * The interrupted status is preserved.
     *
     * @throws CancellationException when the current worker has been interrupted
     */
    public static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("SST work was interrupted");
    }

    /**
     * Creates a checkpoint that checks at most every 256 polls between immediate checks.
     *
     * @param activityCheck immediate request activity check
     */
    public ActivityCheckpoint(Runnable activityCheck) {
        this(activityCheck, DEFAULT_INTERVAL);
    }

    /**
     * Creates a checkpoint with a positive work interval.
     *
     * @param activityCheck immediate request activity check
     * @param interval maximum number of polls before another full check
     * @throws NullPointerException if the activity check is {@code null}
     * @throws IllegalArgumentException if the interval is not positive
     */
    public ActivityCheckpoint(Runnable activityCheck, int interval) {
        this.activityCheck = Objects.requireNonNull(activityCheck, "activityCheck");
        if (interval <= 0)
            throw new IllegalArgumentException("interval must be positive");
        this.interval = interval;
    }

    /**
     * Checks immediately and starts a fresh polling interval after a successful check.
     */
    @Override
    public void run() {
        activityCheck.run();
        remaining = interval - 1;
    }

    /**
     * Checks a batched callback when its work budget is exhausted, or invokes an ordinary
     * callback immediately. Exceptions from the underlying activity check propagate.
     *
     * @param activityCheck callback belonging to the current operation
     * @throws NullPointerException if the callback is {@code null}
     */
    public static void poll(Runnable activityCheck) {
        if (activityCheck instanceof ActivityCheckpoint checkpoint) {
            if (checkpoint.remaining == 0) {
                checkpoint.run();
            } else {
                checkpoint.remaining--;
            }
        } else {
            activityCheck.run();
        }
    }
}
