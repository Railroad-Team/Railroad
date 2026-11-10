package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Computes signature metadata for the given caret position. Implementations may
 * choose their own parsing backend.
 */
@FunctionalInterface
public interface SignatureHelpProvider {
    /**
     * Computes signature help for the supplied document snapshot.
     *
     * @param snapshot the immutable physical or virtual document revision to inspect
     * @param caretPosition zero-based UTF-16 caret offset in the snapshot text
     * @return signature metadata, or {@code null} when the content, language, or invocation is unsupported
     */
    @Nullable
    SignatureHelp compute(DocumentSnapshot snapshot, int caretPosition);

    /**
     * Computes signature help for one captured request and identifies its inputs in the result.
     * The default implementation checks activity before and after a normal return from
     * {@link #compute(WorkspaceSnapshot, DocumentId, int)}, preserving workspace-aware
     * overrides and document-only implementations. Unsupported help still receives the
     * second activity check and a non-null wrapper with a {@code null} payload.
     * <p>
     * Provider exceptions, including cancellation, propagate unchanged. Long-running
     * implementations must cooperate with request controls during computation. These
     * boundary checks are complemented by request identity and current-ness validation
     * at the editor's publication boundary.
     *
     * @param context captured workspace, document, cancellation token, and deadline
     * @param caretPosition zero-based UTF-16 caret offset in the captured document
     * @return a non-null wrapper retaining the exact context and possibly unsupported {@code null} payload
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws CancellationException if cancellation is observed or the provider cancels
     * @throws DeadlineExceededException if the deadline expires
     */
    default @NotNull FeatureResult<SignatureHelp> compute(FeatureRequestContext context, int caretPosition)
        throws CancellationException, DeadlineExceededException {
        Objects.requireNonNull(context, "context");
        context.checkActive();
        SignatureHelp computed = compute(context.workspaceSnapshot(), context.documentSnapshot().id(), caretPosition);
        context.checkActive();
        return new FeatureResult<>(context, computed);
    }

    /**
     * Computes signature help within one workspace, preserving the existing provider contract by default.
     *
     * @param workspace immutable request workspace
     * @param id document to inspect
     * @param caretPosition caret offset in the captured document
     * @return signature help, or {@code null} when unsupported
     */
    default @Nullable SignatureHelp compute(WorkspaceSnapshot workspace, DocumentId id, int caretPosition) {
        return compute(workspace.document(id).orElseThrow(), caretPosition);
    }
}
