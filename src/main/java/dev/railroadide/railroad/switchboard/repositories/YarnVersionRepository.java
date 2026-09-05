package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.SwitchboardRepository;
import dev.railroadide.railroad.switchboard.cache.CacheManager;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Cached access to Yarn versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record YarnVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    /**
     * Returns all Yarn versions.
     *
     * @return a future containing all Yarn versions
     */
    public CompletableFuture<List<String>> getAllVersions() {
        return cache.getOrFetch(
            "yarn:versions",
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            client::fetchYarnVersions);
    }

    /**
     * Returns all Yarn versions synchronously.
     *
     * @return all Yarn versions
     */
    public List<String> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets Yarn versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Yarn versions
     */
    public CompletableFuture<List<String>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "yarn:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            () -> client.fetchYarnVersions(normalized));
    }

    /**
     * Gets compatible Yarn versions synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the compatible Yarn versions
     */
    public List<String> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    /**
     * Returns the latest Yarn version.
     *
     * @return a future containing the latest Yarn version
     */
    public CompletableFuture<String> getLatestVersion() {
        return cache.getOrFetch(
            "yarn:latest",
            String.class,
            LATEST_TTL,
            client::fetchLatestYarnVersion);
    }

    /**
     * Returns the latest Yarn version synchronously.
     *
     * @return the latest Yarn version
     */
    public String getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest Yarn version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Yarn version
     */
    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "yarn:latest:" + normalized;

        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestYarnVersion(normalized));
    }

    /**
     * Gets the latest Yarn version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the latest compatible Yarn version
     */
    public String getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }
}
