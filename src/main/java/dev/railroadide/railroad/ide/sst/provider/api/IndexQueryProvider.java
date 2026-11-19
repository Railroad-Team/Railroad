package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import java.util.List;

/** Queries declarations against the captured workspace, including unsaved document overlays. */
@FunctionalInterface
public interface IndexQueryProvider {
    /**
     * Finds named declarations in the captured workspace.
     *
     * @param context captured workspace, cancellation token, and deadline
     * @param name symbol name to look up according to the language's naming rules
     * @return immutable matching declarations; locations must belong to the captured revisions
     */
    FeatureResult<List<SymbolOccurrence>> query(WorkspaceRequestContext context, String name);
}
