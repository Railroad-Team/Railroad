package dev.railroadide.railroad.switchboard.cache;

import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

public record MetadataCacheEntry<T>(
    T data,
    Instant lastFetched,
    TypeToken<@NotNull T> dataClass,
    Duration ttl,
    String etag
) {
    public boolean isExpired() {
        return Instant.now().isAfter(lastFetched.plus(ttl));
    }
}
