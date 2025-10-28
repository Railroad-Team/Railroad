package dev.railroadide.railroad.ide.signature;

import org.jetbrains.annotations.Nullable;

/**
 * Computes signature metadata for the given caret position. Implementations may
 * choose their own parsing backend.
 */
public interface SignatureHelpProvider {
    /**
     * Computes signature help for the supplied document snapshot.
     *
     * @param document      full document text
     * @param caretPosition zero-based caret offset
     * @return a {@link SignatureHelp} instance describing the active invocation, or {@code null} if none
     */
    @Nullable
    SignatureHelp compute(String document, int caretPosition);
}
