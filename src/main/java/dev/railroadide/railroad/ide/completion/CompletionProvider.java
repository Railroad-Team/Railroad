package dev.railroadide.railroad.ide.completion;

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
 * Provides code completion suggestions for an immutable document revision.
 */
@FunctionalInterface
public interface CompletionProvider {
    /**
     * Selects automatic completion triggers. Called on the editor thread; this policy must be fast and side-effect
     * free.
     * The default supports prefix completion after any insertion. Languages may restrict punctuation or other triggers.
     *
     * @param insertedText newly inserted text, never null
     * @return whether this edit should request completion
     */
    default boolean shouldTrigger(String insertedText) {
        return !insertedText.isEmpty();
    }

    /**
     * Computes completion suggestions for the specified trigger location.
     *
     * @param snapshot the immutable physical or virtual document revision to inspect
     * @param triggerAt zero-based UTF-16 offset of the last inserted character in the snapshot text
     * @return a {@link CompletionResult}, or {@code null} when the content, language, or trigger is unsupported
     */
    @Nullable
    CompletionResult compute(DocumentSnapshot snapshot, int triggerAt);

    /**
     * Computes completion for one captured request and identifies its inputs in the result.
     * The default implementation checks activity before and after a normal return from
     * {@link #compute(WorkspaceSnapshot, DocumentId, int)}, preserving workspace-aware
     * overrides and document-only implementations. An unsupported outcome still receives
     * the second activity check and a non-null wrapper with a {@code null} payload.
     * <p>
     * Provider exceptions, including cancellation, propagate unchanged. Long-running
     * providers must cooperate with request controls during computation; these boundary
     * checks alone do not stop that work. The publication owner separately validates
     * request identity and current editor/workspace state before applying a result.
     *
     * @param context captured workspace, document, cancellation token, and deadline
     * @param triggerAt zero-based UTF-16 trigger offset in the captured document
     * @return a non-null wrapper retaining the exact context and possibly unsupported {@code null} payload
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws CancellationException if cancellation is observed or the provider cancels
     * @throws DeadlineExceededException if the deadline expires
     */
    default @NotNull FeatureResult<CompletionResult> compute(FeatureRequestContext context, int triggerAt)
        throws CancellationException, DeadlineExceededException {
        Objects.requireNonNull(context, "context");
        context.checkActive();
        CompletionResult computed = compute(context.workspaceSnapshot(), context.documentSnapshot().id(), triggerAt);
        context.checkActive();
        return new FeatureResult<>(context, computed);
    }

    /**
     * Computes suggestions within one captured workspace. Existing providers receive its document snapshot.
     *
     * @param workspace immutable request workspace
     * @param id document to inspect
     * @param triggerAt trigger offset
     * @return suggestions, or {@code null} when unsupported
     */
    default @Nullable CompletionResult compute(WorkspaceSnapshot workspace, DocumentId id, int triggerAt) {
        return compute(workspace.document(id).orElseThrow(), triggerAt);
    }
}
