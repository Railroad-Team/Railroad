package dev.railroadide.railroad.ide.sst.request.api;

import java.util.concurrent.CancellationException;

/**
 * Signals that cooperative request work stopped because its deadline expired.
 * This is an expected cancellation outcome, allowing callers to handle it together
 * with other {@link CancellationException} instances while identifying deadline expiry
 * separately for reporting. It does not indicate an internal provider failure.
 */
public final class DeadlineExceededException extends CancellationException {
    /**
     * Creates a deadline-expiry cancellation with a descriptive message.
     */
    public DeadlineExceededException() {
        super("Request deadline exceeded");
    }
}
