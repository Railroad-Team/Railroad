package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import java.util.List;

/** Extracts immutable declarations from one captured text or binary document. */
@FunctionalInterface
public interface IndexExtractionProvider {
    /**
     * Extracts declarations and their versioned locations from the captured document.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @return declarations at this revision; an empty list means no declarations
     */
    FeatureResult<List<SymbolOccurrence>> extract(FeatureRequestContext context);
}
