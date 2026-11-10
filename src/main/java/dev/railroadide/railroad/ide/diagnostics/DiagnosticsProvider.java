package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Provides language diagnostics for a document snapshot.
 *
 * @param <D> the diagnostic type produced by this provider
 */
@FunctionalInterface
public interface DiagnosticsProvider<D extends EditorDiagnostic<?>> {
    /**
     * Computes diagnostics for the supplied {@link DocumentSnapshot}.
     *
     * @param snapshot full document text
     * @return immutable list of diagnostics (empty when none)
     */
    @NotNull
    List<D> compute(DocumentSnapshot snapshot);

    /**
     * Computes diagnostics for one captured request and identifies its inputs in the result.
     * The default implementation checks request activity before and after a normal return
     * from {@link #compute(WorkspaceSnapshot, DocumentId)}, preserving workspace-aware
     * provider overrides and compatibility with document-only implementations.
     * <p>
     * Provider exceptions, including cancellation, propagate unchanged. Boundary checks
     * do not stop work inside an existing provider; long-running implementations must
     * cooperate with the common request controls during computation. Publication owners
     * must also reject superseded requests and stale workspace state before applying results.
     *
     * @param context captured workspace, document, cancellation token, and deadline
     * @return a non-null wrapper retaining the exact supplied context and the provider's immutable diagnostics
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws CancellationException if cancellation is observed or the provider cancels
     * @throws DeadlineExceededException if the deadline expires
     */
    default @NotNull FeatureResult<List<D>> compute(FeatureRequestContext context)
        throws CancellationException, DeadlineExceededException {
        Objects.requireNonNull(context, "context");
        context.checkActive();
        List<D> computed = compute(context.workspaceSnapshot(), context.documentSnapshot().id());
        context.checkActive();
        return new FeatureResult<>(context, computed);
    }

    /**
     * Computes diagnostics within one captured workspace, delegating to existing providers by default.
     *
     * @param workspace immutable request workspace
     * @param id document to inspect
     * @return diagnostics for the captured revision
     */
    default @NotNull List<D> compute(WorkspaceSnapshot workspace, DocumentId id) {
        return compute(workspace.document(id).orElseThrow());
    }
}
