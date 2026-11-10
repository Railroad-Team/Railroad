package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;

import java.util.concurrent.CancellationException;

/**
 * Captured workspace inputs and cooperative execution controls for one request.
 * {@link WorkspaceRequestContext} represents work without a selected document, while
 * {@link FeatureRequestContext} additionally identifies a document in that workspace.
 *
 * <p>
 * Implementations must return stable, non-null snapshot and control instances.
 * The workspace describes captured store state, not a live view or a simultaneous
 * filesystem snapshot. Cancellation and deadline observations can change over time.
 * Concurrent reads require a thread-safe token and deadline time source.
 *
 * <p>
 * The request owner retains the cancellation source; workers receive its token and
 * call {@link #checkActive()} at bounded intervals. Each invocation has its own context
 * instance, even when inputs and controls match another invocation. Activity checks do
 * not establish that captured inputs are still current: publication owners must also
 * reject superseded requests and stale inputs before committing results.
 */
public interface RequestContext {
    /**
     * Returns the immutable workspace captured for this request.
     *
     * @return the same non-null captured workspace on every call
     */
    WorkspaceSnapshot workspaceSnapshot();

    /**
     * Returns the observation-only cancellation handle supplied by the request owner.
     *
     * @return the same non-null token on every call
     */
    CancellationToken cancellationToken();

    /**
     * Returns the fixed deadline shared by work belonging to this request.
     *
     * @return the same non-null deadline, possibly unlimited or already expired
     */
    Deadline deadline();

    /**
     * Checks explicit cancellation first, then deadline expiration, on every call.
     * When both conditions already hold, explicit cancellation takes precedence.
     * Returns normally if neither condition is observed. Expiration does not request
     * cancellation through the token, and neither outcome changes the captured inputs.
     *
     * @throws CancellationException if the token reports cancellation; the token's exception is propagated
     * @throws DeadlineExceededException if the token check returns normally and the deadline is expired
     */
    default void checkActive() {
        cancellationToken().throwIfCancellationRequested();
        if (deadline().isExpired())
            throw new DeadlineExceededException();
    }
}
