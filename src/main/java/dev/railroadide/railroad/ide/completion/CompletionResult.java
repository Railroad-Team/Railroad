package dev.railroadide.railroad.ide.completion;

import java.util.Collections;
import java.util.List;

/**
 * Result payload describing completions for a particular trigger index.
 *
 * @param dotIndex source offset of the completion trigger, or -1 for an empty result
 * @param items completion candidates
 */
public record CompletionResult(int dotIndex, List<CompletionItem> items) {
    /**
     * Creates a completion result without a trigger or candidates.
     *
     * @return empty result with trigger offset -1
     */
    public static CompletionResult empty() {
        return new CompletionResult(-1, Collections.emptyList());
    }
}
