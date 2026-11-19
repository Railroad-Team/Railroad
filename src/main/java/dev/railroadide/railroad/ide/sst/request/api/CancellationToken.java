package dev.railroadide.railroad.ide.sst.request.api;

import java.util.concurrent.CancellationException;

/**
 * Observation-only handle for cooperative cancellation of an SST operation.
 * The request owner retains a {@link CancellationTokenSource} and supplies its token
 * to workers, which check it at bounded intervals and stop when cancellation is requested.
 * Requesting cancellation does not interrupt a worker or wait for it to finish.
 * <p>
 * Implementations must support concurrent reads and safely expose cancellation across
 * threads. Once cancellation is requested, the token must never return to an uncancelled
 * state. Checking cancellation does not consume or clear that state.
 */
@FunctionalInterface
public interface CancellationToken {
    /**
     * A shared token that never requests cancellation.
     * Its cancellation check always returns normally, including on an interrupted thread.
     */
    CancellationToken NONE = () -> false;

    /**
     * Reports whether the owner has requested cancellation.
     * A {@code false} result does not prevent cancellation immediately after this check.
     *
     * @return {@code true} if cancellation has been requested, otherwise {@code false}
     */
    boolean isCancellationRequested();

    /**
     * Checks cancellation and returns normally if it has not been requested.
     * Cancellation is an expected request outcome; callers should stop work and avoid
     * publishing partial results as complete rather than reporting an internal failure.
     * This method does not read or change the calling thread's interrupt status.
     *
     * @throws CancellationException if cancellation is requested when checked
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested())
            throw new CancellationException();
    }

    /**
     * Returns a shared token that never requests cancellation.
     * Its cancellation check always returns normally, including on an interrupted thread.
     *
     * @return the shared, permanently uncancelled token
     */
    static CancellationToken none() {
        return NONE;
    }
}
