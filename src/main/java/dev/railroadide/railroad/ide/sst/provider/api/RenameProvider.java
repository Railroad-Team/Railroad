package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;

/**
 * Prepares and computes semantic renames of the symbol at a source position without mutating the workspace.
 * <p>
 * Renames are computed from resolved references in the request's captured workspace, including unsaved documents,
 * and are returned as a snapshot-bound {@link dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit} that hosts
 * preview and apply through the workspace-edit applier. Providers never guess: a position that resolves to no
 * symbol, to several symbols, or to a symbol whose declarations or references cannot all be updated is rejected,
 * and every change in meaning a rename would cause is reported as a {@link RenameConflict}.
 * <p>
 * Methods run on SST workers, must observe the context's cancellation and deadline, and must return immutable
 * payloads.
 */
public interface RenameProvider {
    /**
     * Checks whether the symbol at a position can be renamed.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @param offset UTF-16 offset of the identifier to rename, or the offset just after it
     * @return the renameable symbol and the identifier range at the position, or the reason it cannot be renamed
     */
    FeatureResult<RenamePreparation> prepareRename(FeatureRequestContext context, int offset);

    /**
     * Computes the edit that renames the symbol at a position, together with any conflicts it would cause.
     * <p>
     * The workspace may have changed since the symbol was prepared. The rename is rejected unless the identifier
     * at the offset still names exactly the prepared symbol in the context's workspace, so a rename never moves to
     * a different declaration or overload.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @param offset UTF-16 offset accepted by {@link #prepareRename}
     * @param symbol identity returned by {@link #prepareRename} as {@link RenamePreparation.Renameable#symbol()}
     * @param newName requested new name
     * @return the proposed edit and its conflicts; the edit is absent when an error conflict prevents the rename
     */
    FeatureResult<RenameProposal> rename(FeatureRequestContext context, int offset, SymbolId symbol, String newName);
}
