package dev.railroadide.railroad.ide.sst.request.api;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

/**
 * Owns the cancellation state of one SST request and exposes an observation-only token.
 * A source owns a cancellation flag and can also observe a parent lifetime token.
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
     * Creates a source whose stable token observes both local cancellation and a parent lifetime.
     * Cancelling this source never cancels the parent. The parent must obey the token's monotonic,
     * thread-safe cancellation contract. No listener or external resource is allocated.
     *
     * @param parent lifetime observed in addition to this source's own cancellation flag
     * @throws NullPointerException if the parent is null
     */
    public CancellationTokenSource(CancellationToken parent) {
        Objects.requireNonNull(parent, "parent");
        this.token = () -> isCancellationRequested.get() || parent.isCancellationRequested();
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
     * local uncancelled-to-cancelled transition and reports success, independently of parent cancellation.
     *
     * @return {@code true} if this call first requested local cancellation, or {@code false}
     *         if local cancellation had already been requested
     */
    public boolean cancel() {
        return isCancellationRequested.compareAndSet(false, true);
    }
}
