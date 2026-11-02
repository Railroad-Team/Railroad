package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import org.jetbrains.annotations.Nullable;

/**
 * Provides code completion suggestions for an immutable document revision.
 */
@FunctionalInterface
public interface CompletionProvider {
    /**
     * Computes completion suggestions for the specified trigger location.
     *
     * @param snapshot the immutable physical or virtual document revision to inspect
     * @param triggerAt zero-based UTF-16 offset of the trigger character in the snapshot text (e.g. '.')
     * @return a {@link CompletionResult}, or {@code null} when the content, language, or trigger is unsupported
     */
    @Nullable
    CompletionResult compute(DocumentSnapshot snapshot, int triggerAt);

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
