package dev.railroadide.railroad.ide.sst.request.api;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the cancellation state of one SST request and exposes an observation-only token.
 * A source starts uncancelled and can transition permanently to cancelled exactly once.
 * The request owner retains the source; workers receive {@link #token()}.
 * <p>
 * Sources and their tokens support concurrent use. Cancellation is cooperative:
 * {@link #cancel()} records a request without interrupting threads or waiting for workers
 * to stop. Owners create a new source for each independent request. Sources hold no
 * external resources and need no disposal; release references when the request ends.
 */
public final class CancellationTokenSource {
    private final CancellationToken token;
    private final AtomicBoolean isCancellationRequested = new AtomicBoolean(false);

    /**
     * Creates an uncancelled source with a stable token bound to its cancellation state.
     */
    public CancellationTokenSource() {
        this.token = isCancellationRequested::get;
    }

    /**
     * Returns the observation-only token for this source.
     * Every call returns the same instance, and previously obtained tokens observe later
     * cancellation requests.
     *
     * @return this source's stable cancellation token
     */
    public CancellationToken token() {
        return token;
    }

    /**
     * Permanently requests cancellation without waiting for workers to stop.
     * Repeated calls are harmless. When calls race, exactly one call performs the
     * uncancelled-to-cancelled transition and reports success.
     *
     * @return {@code true} if this call first requested cancellation, or {@code false}
     *         if cancellation had already been requested
     */
    public boolean cancel() {
        return isCancellationRequested.compareAndSet(false, true);
    }
}
