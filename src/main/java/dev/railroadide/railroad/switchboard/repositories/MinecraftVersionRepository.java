package dev.railroadide.railroad.switchboard.repositories;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.core.switchboard.SwitchboardRepository;
import dev.railroadide.core.switchboard.cache.CacheManager;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.switchboard.SwitchboardClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public record MinecraftVersionRepository(SwitchboardClient client, CacheManager cache)
    implements SwitchboardRepository {
    public CompletableFuture<List<MinecraftVersion>> getAllVersions() {
        return cache.getOrFetch(
            "mc:versions",
            new TypeToken<List<MinecraftVersion>>() {
            },
            Duration.ofHours(12),
            client::fetchMinecraftVersions
        );
    }

    public List<MinecraftVersion> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    public CompletableFuture<Optional<MinecraftVersion>> getVersion(String id) {
        String key = "mc:version:" + id.toLowerCase(Locale.ROOT);
        return cache.getOrFetchOptional(
            key,
            MinecraftVersion.class,
            Duration.ofDays(7),
            () -> client.fetchMinecraftVersionById(id)
        );
    }

    public Optional<MinecraftVersion> getVersionSync(String id) throws ExecutionException, InterruptedException {
        return getVersion(id).get();
    }

    public CompletableFuture<MinecraftVersion> getLatestVersion() {
        return cache.getOrFetch(
            "mc:latest",
            MinecraftVersion.class,
            Duration.ofHours(1),
            client::fetchLatestMinecraftVersion
        );
    }

    public MinecraftVersion getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    public CompletableFuture<MinecraftVersion> getLatest(MinecraftVersion.Type type) {
        String key = "mc:latest:" + type.name().toLowerCase(Locale.ROOT);
        return cache.getOrFetch(
            key,
            MinecraftVersion.class,
            Duration.ofHours(1),
            () -> client.fetchLatestMinecraftVersionOfType(type)
        );
    }

    public MinecraftVersion getLatestSync(MinecraftVersion.Type type) throws ExecutionException, InterruptedException {
        return getLatest(type).get();
    }
}
