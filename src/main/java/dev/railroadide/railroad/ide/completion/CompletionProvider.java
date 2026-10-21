package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
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
}
