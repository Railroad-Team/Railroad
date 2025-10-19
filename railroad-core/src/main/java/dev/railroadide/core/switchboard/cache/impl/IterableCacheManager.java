package dev.railroadide.core.switchboard.cache.impl;

import dev.railroadide.core.switchboard.cache.CacheEntryWrapper;
import dev.railroadide.core.switchboard.cache.CacheManager;

public interface IterableCacheManager extends CacheManager {
    Iterable<CacheEntryWrapper> entries();
}
