package dev.railroadide.railroad.switchboard.cache;


import com.google.gson.reflect.TypeToken;

public record CacheEntryWrapper(
    String key,
    MetadataCacheEntry<?> entry,
    TypeToken<?> typeToken
) {}
