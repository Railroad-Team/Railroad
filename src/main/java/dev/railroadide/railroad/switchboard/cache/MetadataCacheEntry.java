package dev.railroadide.railroad.switchboard.cache;

import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/**
 * A cached value and the metadata used to determine its validity.
 *
 * @param <T> the cached value type
 * @param data the cached value
 * @param lastFetched the time at which the value was retrieved
 * @param dataClass the type token for the cached value
 * @param ttl the duration for which the value remains valid
 * @param etag the optional remote entity tag associated with the value
 */
public record MetadataCacheEntry<T>(
    T data,
    Instant lastFetched,
    TypeToken<@NotNull T> dataClass,
    Duration ttl,
    String etag
) {
    /**
     * Determines whether this entry is older than its time-to-live.
     *
     * @return {@code true} when the entry has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(lastFetched.plus(ttl));
    }
}
