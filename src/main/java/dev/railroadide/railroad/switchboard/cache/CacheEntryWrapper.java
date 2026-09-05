package dev.railroadide.railroad.switchboard.cache;

import com.google.gson.reflect.TypeToken;

/**
 * A cache entry together with the key and type information needed to migrate it.
 *
 * @param key the cache key
 * @param entry the cached metadata and value
 * @param typeToken the type token for the cached value
 */
public record CacheEntryWrapper(
    String key,
    MetadataCacheEntry<?> entry,
    TypeToken<?> typeToken
) {
}
