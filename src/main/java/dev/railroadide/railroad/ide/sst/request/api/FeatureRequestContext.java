package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/**
 * Captured inputs and cooperative execution controls for one document feature request.
 * Text and binary requests share this contract. The document must be the exact snapshot
 * instance captured by the workspace; matching only its logical identity and content
 * version would overlook changes to location or other metadata.
 * <p>
 * Snapshots and controls are retained directly. Snapshot versions describe the captured
 * input, while cancellation and deadline queries continue to reflect their current state.
 * Already-cancelled or expired requests may be constructed and inspected. Cancellation
 * does not invalidate the retained snapshots or make them unreadable.
 * <p>
 * Instances support concurrent reads when their cancellation token and deadline time
 * source satisfy their thread-safety contracts. Workers call {@link #checkActive()} at
 * bounded intervals. That check reports activity at the time of observation; it neither
 * reserves execution time nor validates a result against the live editor or workspace.
 * The publication owner must separately reject superseded or stale results.
 *
 * @param workspaceSnapshot immutable workspace captured for this request
 * @param documentSnapshot exact document instance contained in the captured workspace
 * @param cancellationToken observation-only cancellation handle for the request
 * @param deadline fixed time budget for the request, or {@link Deadline#none()} for no limit
 */
public record FeatureRequestContext(
    WorkspaceSnapshot workspaceSnapshot,
    DocumentSnapshot documentSnapshot,
    CancellationToken cancellationToken,
    Deadline deadline
) implements RequestContext {
    /**
     * Validates and retains one coherent set of captured inputs and execution controls.
     * Construction accepts cancelled tokens and expired deadlines; activity is checked
     * explicitly through {@link #checkActive()} when work runs.
     *
     * @param workspaceSnapshot immutable workspace captured for this request
     * @param documentSnapshot exact document instance contained in the captured workspace
     * @param cancellationToken observation-only cancellation handle for the request
     * @param deadline request time budget, including an unlimited or already-expired budget
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the workspace does not contain this exact document instance
     */
    public FeatureRequestContext {
        Objects.requireNonNull(workspaceSnapshot, "workspaceSnapshot");
        Objects.requireNonNull(documentSnapshot, "documentSnapshot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(deadline, "deadline");

        Optional<DocumentSnapshot> document = workspaceSnapshot.document(documentSnapshot.id());
        if (document.isEmpty())
            throw new IllegalArgumentException("documentSnapshot is not part of workspaceSnapshot");

        if (!Objects.equals(document.get(), documentSnapshot))
            throw new IllegalArgumentException("documentSnapshot does not match the workspaceSnapshot's document");
    }
}
