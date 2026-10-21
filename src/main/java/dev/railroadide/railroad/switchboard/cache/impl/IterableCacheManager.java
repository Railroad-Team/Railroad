package dev.railroadide.railroad.switchboard.cache.impl;

import dev.railroadide.railroad.switchboard.cache.CacheEntryWrapper;
import dev.railroadide.railroad.switchboard.cache.CacheManager;

/**
 * A cache manager that can enumerate its stored entries.
 */
public interface IterableCacheManager extends CacheManager {
    /**
     * Returns the entries currently available for migration or inspection.
     *
     * @return the cache entries
     */
    Iterable<CacheEntryWrapper> entries();
}
