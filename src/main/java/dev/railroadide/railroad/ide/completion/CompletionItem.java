package dev.railroadide.railroad.ide.completion;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable model representing a single completion entry.
 *
 * @param insertText  text inserted into the editor when selected
 * @param displayText text shown to the user in the completion list
 */
public record CompletionItem(String insertText, String displayText) {
    @Override
    public @NotNull String toString() {
        return displayText;
    }
}
