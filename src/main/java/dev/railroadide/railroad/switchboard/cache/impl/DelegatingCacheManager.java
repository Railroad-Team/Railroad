package dev.railroadide.railroad.switchboard.cache.impl;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.cache.MetadataCacheEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DelegatingCacheManager implements CacheManager {
    private volatile CacheManager delegate;

    public DelegatingCacheManager(CacheManager initial) {
        this.delegate = initial;
    }

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
