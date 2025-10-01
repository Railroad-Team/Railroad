package dev.railroadide.railroad.switchboard.cache;

import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface CacheManager {
    <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, TypeToken<@NotNull T> typeToken);

    default <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, Class<T> type) {
        return get(key, TypeToken.get(type));
    }

    <T> T put(String key, MetadataCacheEntry<T> entry);

    default <T> T put(String key, T data, Duration ttl, TypeToken<@NotNull T> typeToken) {
        return put(key, new MetadataCacheEntry<>(data, Instant.now(), typeToken, ttl, null));
    }

    void invalidate(String key);

    default <T> CompletableFuture<T> getOrFetch(
        String key,
        TypeToken<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<T>> fetcher
    ) {
        return get(key, typeToken).thenCompose(opt ->
            opt.map(entry -> CompletableFuture.completedFuture(entry.data()))
                .orElseGet(() -> fetcher.get().thenApply(fresh -> {
                    put(key, fresh, ttl, typeToken);
                    return fresh;
                }))
        );
    }

    default <T> CompletableFuture<T> getOrFetch(
        String key,
        Class<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<T>> fetcher
    ) {
        return getOrFetch(key, TypeToken.get(typeToken), ttl, fetcher);
    }

    default <T> CompletableFuture<Optional<T>> getOrFetchOptional(
        String key,
        TypeToken<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<Optional<T>>> fetcher
    ) {
        return get(key, typeToken).thenCompose(opt ->
            opt.map(entry -> CompletableFuture.completedFuture(Optional.of(entry.data())))
                .orElseGet(() -> fetcher.get().thenApply(freshOpt -> {
                    freshOpt.ifPresent(fresh -> put(key, fresh, ttl, typeToken));
                    return freshOpt;
                }))
        );
    }

    default <T> CompletableFuture<Optional<T>> getOrFetchOptional(
        String key,
        Class<T> typeToken,
        Duration ttl,
        Supplier<CompletableFuture<Optional<T>>> fetcher
    ) {
        return getOrFetchOptional(key, TypeToken.get(typeToken), ttl, fetcher);
    }
}
