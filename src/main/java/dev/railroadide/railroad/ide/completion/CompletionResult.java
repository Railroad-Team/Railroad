package dev.railroadide.railroad.ide.completion;

import java.util.Collections;
import java.util.List;

/**
 * Immutable completion candidates and the start of the text replaced by a selected candidate.
 *
 * @param replacementStart inclusive UTF-16 replacement offset; the live caret supplies the exclusive end,
 *            or -1 when there are no candidates
 * @param items completion candidates
 */
public record CompletionResult(int replacementStart, List<CompletionItem> items) {
    /**
     * Creates a result with an immutable copy of the completion candidates.
     *
     * @param replacementStart inclusive UTF-16 replacement offset, or -1 only when there are no candidates
     * @param items non-null completion candidates to copy
     * @throws IllegalArgumentException if the offset is below -1 or is -1 with nonempty candidates
     */
    public CompletionResult {
        items = List.copyOf(items);
        if (replacementStart < -1 || replacementStart == -1 && !items.isEmpty())
            throw new IllegalArgumentException("Completion replacement start must be nonnegative for candidates");
    }
    /**
     * Creates a completion result without a trigger or candidates.
     *
     * @return empty result with trigger offset -1
     */
    public static CompletionResult empty() {
        return new CompletionResult(-1, Collections.emptyList());
    }
}
