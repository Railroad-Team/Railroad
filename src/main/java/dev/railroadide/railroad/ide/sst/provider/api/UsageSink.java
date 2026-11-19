package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;

import java.util.List;

/**
 * Receives incremental usage results on the provider's worker thread. Implementations must be thread-safe, must
 * not block, and must not retain batches after the owning request is cancelled.
 */
@FunctionalInterface
public interface UsageSink {
    /**
     * Sink that discards partial results.
     */
    UsageSink NONE = (_, _, _) -> {
    };

    /**
     * Accepts one batch of usages, all belonging to captured document revisions, together with search progress.
     *
     * @param batch immutable usages found since the previous batch, possibly empty for a progress-only update
     * @param searchedDocuments number of documents searched so far
     * @param totalDocuments number of documents the search will visit
     */
    void accept(List<Location<?>> batch, int searchedDocuments, int totalDocuments);
}
