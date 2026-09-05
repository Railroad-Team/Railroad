package dev.railroadide.railroad.switchboard.cache;

import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Stores and retrieves metadata entries from a cache backend.
 */
public interface CacheManager {
    /**
     * Retrieves a non-expired entry by key.
     *
     * @param key the cache key
     * @param typeToken the type token for the cached value
     * @param <T> the cached value type
     * @return a future containing the entry, or empty when no valid entry exists
     */
    <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, TypeToken<@NotNull T> typeToken);

    /**
     * Retrieves a non-expired entry by key using a raw class token.
     *
     * @param key the cache key
     * @param type the class of the cached value
     * @param <T> the cached value type
     * @return a future containing the entry, or empty when no valid entry exists
     */
    default <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, Class<T> type) {
        return get(key, TypeToken.get(type));
    }

    /**
     * Stores an entry in the cache.
     *
     * @param key the cache key
     * @param entry the entry to store
     * @param <T> the cached value type
     * @return the stored value
     */
    <T> T put(String key, MetadataCacheEntry<T> entry);

    /**
     * Creates and stores an entry using the current time as its fetch time.
     *
     * @param key the cache key
     * @param data the value to store
     * @param ttl the amount of time the value remains valid
     * @param typeToken the type token for the value
     * @param <T> the cached value type
     * @return the stored value
     */
    default <T> T put(String key, T data, Duration ttl, TypeToken<@NotNull T> typeToken) {
        return put(key, new MetadataCacheEntry<>(data, Instant.now(), typeToken, ttl, null));
    }

    /**
     * Removes an entry from the cache.
     *
     * @param key the cache key to invalidate
     */
    void invalidate(String key);

    /**
     * Returns a cached value or fetches and caches it when absent or expired.
     *
     * @param key the cache key
     * @param typeToken the type token for the fetched value
     * @param ttl the amount of time the fetched value remains valid
     * @param fetcher the asynchronous value supplier
     * @param <T> the value type
     * @return a future containing the cached or freshly fetched value
     */
    default <T> CompletableFuture<T> getOrFetch(
        String key,
        TypeToken<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<T>> fetcher
    ) {
        return get(key, typeToken).thenCompose(opt -> opt.map(entry -> CompletableFuture.completedFuture(entry.data()))
            .orElseGet(() -> fetcher.get().thenApply(fresh -> {
                put(key, fresh, ttl, typeToken);
                return fresh;
            })));
    }

    /**
     * Returns a cached value or fetches and caches it using a class token.
     *
     * @param key the cache key
     * @param typeToken the class of the fetched value
     * @param ttl the amount of time the fetched value remains valid
     * @param fetcher the asynchronous value supplier
     * @param <T> the value type
     * @return a future containing the cached or freshly fetched value
     */
    default <T> CompletableFuture<T> getOrFetch(
        String key,
        Class<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<T>> fetcher
    ) {
        return getOrFetch(key, TypeToken.get(typeToken), ttl, fetcher);
    }

    /**
     * Returns an optional cached value or fetches and optionally caches it when absent.
     *
     * @param key the cache key
     * @param typeToken the type token for the fetched value
     * @param ttl the amount of time the fetched value remains valid
     * @param fetcher the asynchronous optional-value supplier
     * @param <T> the value type
     * @return a future containing the cached or freshly fetched optional value
     */
    default <T> CompletableFuture<Optional<T>> getOrFetchOptional(
        String key,
        TypeToken<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<Optional<T>>> fetcher
    ) {
        return get(key, typeToken)
            .thenCompose(opt -> opt.map(entry -> CompletableFuture.completedFuture(Optional.of(entry.data())))
                .orElseGet(() -> fetcher.get().thenApply(freshOpt -> {
                    freshOpt.ifPresent(fresh -> put(key, fresh, ttl, typeToken));
                    return freshOpt;
                })));
    }

    /**
     * Returns an optional cached value or fetches it using a class token.
     *
     * @param key the cache key
     * @param typeToken the class of the fetched value
     * @param ttl the amount of time the fetched value remains valid
     * @param fetcher the asynchronous optional-value supplier
     * @param <T> the value type
     * @return a future containing the cached or freshly fetched optional value
     */
    default <T> CompletableFuture<Optional<T>> getOrFetchOptional(
        String key,
        Class<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<Optional<T>>> fetcher
    ) {
        return getOrFetchOptional(key, TypeToken.get(typeToken), ttl, fetcher);
    }
}
