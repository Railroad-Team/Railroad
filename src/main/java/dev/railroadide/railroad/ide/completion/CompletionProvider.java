package dev.railroadide.railroad.ide.completion;

import org.jetbrains.annotations.Nullable;

/**
 * Abstraction for providing code completion suggestions.
 */
public interface CompletionProvider {
    /**
     * Computes completion suggestions for the specified trigger location.
     *
     * @param document  full document snapshot
     * @param triggerAt index of the trigger character (e.g. '.')
     * @return a {@link CompletionResult} or {@code null} if completions are not available
     */
    @Nullable
    CompletionResult compute(String document, int triggerAt);
}
