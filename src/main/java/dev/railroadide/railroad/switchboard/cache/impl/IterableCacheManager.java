package dev.railroadide.railroad.switchboard.cache.impl;

import dev.railroadide.railroad.switchboard.cache.CacheEntryWrapper;
import dev.railroadide.railroad.switchboard.cache.CacheManager;

public interface IterableCacheManager extends CacheManager {
    Iterable<CacheEntryWrapper> entries();
}
