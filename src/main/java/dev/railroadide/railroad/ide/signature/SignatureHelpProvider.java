package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
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

    /**
     * Computes signature help within one workspace, preserving the existing provider contract by default.
     *
     * @param workspace immutable request workspace
     * @param id document to inspect
     * @param caretPosition caret offset in the captured document
     * @return signature help, or {@code null} when unsupported
     */
    default @Nullable SignatureHelp compute(WorkspaceSnapshot workspace, DocumentId id, int caretPosition) {
        return compute(workspace.document(id).orElseThrow(), caretPosition);
    }
}
