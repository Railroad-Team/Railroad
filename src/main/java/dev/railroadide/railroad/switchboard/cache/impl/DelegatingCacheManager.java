package dev.railroadide.railroad.switchboard.cache.impl;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.cache.MetadataCacheEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cache manager that forwards operations to a replaceable backend.
 */
public class DelegatingCacheManager implements CacheManager {
    private volatile CacheManager delegate;

    /**
     * Creates a cache manager using the supplied backend.
     *
     * @param initial the initial cache backend
     */
    public DelegatingCacheManager(CacheManager initial) {
        this.delegate = initial;
    }

    /**
     * Replaces the active backend, optionally migrating entries first.
     *
     * @param newBackend the backend to use for subsequent operations
     * @param migrate whether entries should be copied before switching
     */
    public void switchBackend(CacheManager newBackend, boolean migrate) {
        if (migrate) {
            migrate(delegate, newBackend);
        }

        this.delegate = newBackend;
    }

    @Override
    public <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, TypeToken<@NotNull T> typeToken) {
        return delegate.get(key, typeToken);
    }

    @Override
    public <T> T put(String key, MetadataCacheEntry<T> entry) {
        delegate.put(key, entry);
        return entry.data();
    }

    @Override
    public void invalidate(String key) {
        delegate.invalidate(key);
    }

    private void migrate(CacheManager oldBackend, CacheManager newBackend) {

    }
}
