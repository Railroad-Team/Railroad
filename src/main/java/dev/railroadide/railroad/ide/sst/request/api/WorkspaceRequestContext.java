package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;

import java.util.Objects;

/**
 * Captured inputs and cooperative controls for work without a selected document.
 * An empty workspace is valid. Text and binary documents, when present, remain available
 * through the captured workspace without requiring either kind of document as an anchor.
 *
 * <p>
 * Snapshots and controls are retained directly. Already-cancelled tokens and expired
 * deadlines are accepted; workers check activity through {@link #checkActive()}.
 * Capturing this context does not read missing files, freeze external indexes, or
 * establish that a result may be published against current workspace state.
 *
 * <p>
 * When dispatching document work from this request, callers can construct a
 * {@link FeatureRequestContext} using a document from this workspace and the same token
 * and deadline. Sharing the existing deadline preserves the overall time budget.
 * Each dispatched invocation still needs its own context instance for result correlation.
 *
 * @param workspaceSnapshot immutable workspace captured for this request
 * @param cancellationToken observation-only cancellation handle for the request
 * @param deadline fixed request budget, or {@link Deadline#none()} for no limit
 */
public record WorkspaceRequestContext(
    WorkspaceSnapshot workspaceSnapshot,
    CancellationToken cancellationToken,
    Deadline deadline
) implements RequestContext {
    /**
     * Validates and retains the captured workspace and execution controls.
     *
     * @param workspaceSnapshot captured workspace, which may contain no documents
     * @param cancellationToken request cancellation handle, possibly already cancelled
     * @param deadline fixed request budget, possibly already expired
     * @throws NullPointerException if any component is {@code null}
     */
    public WorkspaceRequestContext {
        Objects.requireNonNull(workspaceSnapshot, "workspaceSnapshot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(deadline, "deadline");
    }
}
