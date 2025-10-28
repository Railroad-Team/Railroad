package dev.railroadide.railroad.ide.completion;

import java.util.Collections;
import java.util.List;

/**
 * Result payload describing completions for a particular trigger index.
 */
public record CompletionResult(int dotIndex, List<CompletionItem> items) {
    public static CompletionResult empty() {
        return new CompletionResult(-1, Collections.emptyList());
    }
}
