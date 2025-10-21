package dev.railroadide.core.switchboard.cache.impl;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.core.switchboard.cache.CacheEntryWrapper;
import dev.railroadide.core.switchboard.cache.CacheManager;
import dev.railroadide.core.switchboard.cache.MetadataCacheEntry;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.logger.Logger;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MigratingCacheManager implements CacheManager {
    private final CacheManager oldBackend;
    private final CacheManager newBackend;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean sweepStarted = false;
    @Setter
    @Getter
    private long sweepThrottleMs = 10;

    public MigratingCacheManager(CacheManager oldBackend, CacheManager newBackend) {
        this.oldBackend = oldBackend;
        this.newBackend = newBackend;
    }

    @Override
    public <T> CompletableFuture<Optional<MetadataCacheEntry<T>>> get(String key, TypeToken<@NotNull T> typeToken) {
        // we will try see if the new backend has it first
        return newBackend.get(key, typeToken).thenCompose(opt -> {
            if (opt.isPresent())
                return CompletableFuture.completedFuture(opt);

            // if not, fetch from old backend and copy to new backend
            return oldBackend.get(key, typeToken).thenApply(oldOpt -> {
                oldOpt.ifPresent(entry -> newBackend.put(key, entry));
                return oldOpt;
            });
        });
    }

    @Override
    public <T> T put(String key, MetadataCacheEntry<T> entry) {
        // we only want to write to the new backend
        newBackend.put(key, entry);
        return entry.data();
    }

    @Override
    public void invalidate(String key) {
        // we only want to invalidate in the new backend, otherwise we might re-migrate it
        newBackend.invalidate(key);
    }

    public void startBackgroundSweep() {
        if (sweepStarted)
            return;

        sweepStarted = true;

        executor.submit(() -> {
            try {
                if (!(oldBackend instanceof IterableCacheManager iterableOld)) {
                    ServiceLocator.getService(Logger.class).warn("Old cache backend does not support iteration, skipping sweep.");
                    return;
                }

                for (CacheEntryWrapper entry : iterableOld.entries()) {
                    try {
                        // Don’t overwrite if already migrated
                        if (newBackend.get(entry.key(), entry.typeToken()).join().isEmpty()) {
                            newBackend.put(entry.key(), entry.entry());
                        }
                    } catch (Exception exception) {
                        ServiceLocator.getService(Logger.class).error("Failed to migrate cache entry: {}", entry.key(), exception);
                    }

                    // we throttle a bit to avoid hogging hammering the disk
                    Thread.sleep(this.sweepThrottleMs);
                }

                ServiceLocator.getService(Logger.class).info("Cache migration sweep complete.");
            } catch (Exception exception) {
                ServiceLocator.getService(Logger.class).error("Cache migration sweep failed", exception);
            } finally {
                executor.shutdown();
            }
        });
    }
}
