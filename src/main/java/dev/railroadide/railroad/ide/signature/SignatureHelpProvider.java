package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import org.jetbrains.annotations.Nullable;

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
}
