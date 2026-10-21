package dev.railroadide.railroad.switchboard.repositories;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.SwitchboardRepository;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.pojo.MinecraftVersion;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Cached access to Minecraft versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record MinecraftVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    /**
     * Returns all Minecraft versions.
     *
     * @return a future containing all Minecraft versions
     */
    public CompletableFuture<List<MinecraftVersion>> getAllVersions() {
        return cache.getOrFetch(
            "mc:versions",
            new TypeToken<List<MinecraftVersion>>() {
            },
            Duration.ofHours(12),
            client::fetchMinecraftVersions);
    }

    /**
     * Returns all Minecraft versions synchronously.
     *
     * @return all Minecraft versions
     */
    public List<MinecraftVersion> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets a Minecraft version by identifier.
     *
     * @param id the Minecraft version identifier
     * @return a future containing the version, or empty when it is unavailable
     */
    public CompletableFuture<Optional<MinecraftVersion>> getVersion(String id) {
        String key = "mc:version:" + id.toLowerCase(Locale.ROOT);
        return cache.getOrFetchOptional(
            key,
            MinecraftVersion.class,
            Duration.ofDays(7),
            () -> client.fetchMinecraftVersionById(id));
    }

    /**
     * Gets a Minecraft version by identifier synchronously.
     *
     * @param id the Minecraft version identifier
     * @return the version, or empty when it is unavailable
     */
    public Optional<MinecraftVersion> getVersionSync(String id) throws ExecutionException, InterruptedException {
        return getVersion(id).get();
    }

    /**
     * Returns the latest Minecraft version.
     *
     * @return a future containing the latest Minecraft version
     */
    public CompletableFuture<MinecraftVersion> getLatestVersion() {
        return cache.getOrFetch(
            "mc:latest",
            MinecraftVersion.class,
            Duration.ofHours(1),
            client::fetchLatestMinecraftVersion);
    }

    /**
     * Returns the latest Minecraft version synchronously.
     *
     * @return the latest Minecraft version
     */
    public MinecraftVersion getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest Minecraft version of a specific type.
     *
     * @param type the Minecraft version type
     * @return a future containing the latest version of that type
     */
    public CompletableFuture<MinecraftVersion> getLatest(MinecraftVersion.Type type) {
        String key = "mc:latest:" + type.name().toLowerCase(Locale.ROOT);
        return cache.getOrFetch(
            key,
            MinecraftVersion.class,
            Duration.ofHours(1),
            () -> client.fetchLatestMinecraftVersionOfType(type));
    }

    /**
     * Gets the latest Minecraft version of a specific type synchronously.
     *
     * @param type the Minecraft version type
     * @return the latest version of that type
     */
    public MinecraftVersion getLatestSync(MinecraftVersion.Type type) throws ExecutionException, InterruptedException {
        return getLatest(type).get();
    }
}
