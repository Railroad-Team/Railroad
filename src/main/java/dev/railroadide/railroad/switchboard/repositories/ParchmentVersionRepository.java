package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.SwitchboardRepository;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.pojo.ParchmentVersion;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Cached access to Parchment versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record ParchmentVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    /**
     * Returns all Parchment versions.
     *
     * @return a future containing all Parchment versions
     */
    public CompletableFuture<List<ParchmentVersion>> getAllVersions() {
        return cache.getOrFetch(
            "parchment:versions",
            SwitchboardClient.LIST_OF_PARCHMENT_VERSIONS,
            VERSIONS_TTL,
            client::fetchParchmentVersions);
    }

    /**
     * Returns all Parchment versions synchronously.
     *
     * @return all Parchment versions
     */
    public List<ParchmentVersion> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets Parchment versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Parchment versions
     */
    public CompletableFuture<List<ParchmentVersion>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "parchment:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_PARCHMENT_VERSIONS,
            VERSIONS_TTL,
            () -> client.fetchParchmentVersions(normalized));
    }

    /**
     * Gets compatible Parchment versions synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the compatible Parchment versions
     */
    public List<ParchmentVersion> getVersionsForSync(String minecraftVersionId)
        throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    /**
     * Returns the latest Parchment version.
     *
     * @return a future containing the latest Parchment version
     */
    public CompletableFuture<ParchmentVersion> getLatestVersion() {
        return cache.getOrFetch(
            "parchment:latest",
            ParchmentVersion.class,
            LATEST_TTL,
            client::fetchLatestParchmentVersion);
    }

    /**
     * Returns the latest Parchment version synchronously.
     *
     * @return the latest Parchment version
     */
    public ParchmentVersion getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest Parchment version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Parchment version
     */
    public CompletableFuture<ParchmentVersion> getLatestVersionFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "parchment:latest:" + normalized;

        return cache.getOrFetch(
            key,
            ParchmentVersion.class,
            LATEST_TTL,
            () -> client.fetchLatestParchmentVersion(normalized));
    }

    /**
     * Gets the latest Parchment version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the latest compatible Parchment version
     */
    public ParchmentVersion getLatestVersionForSync(String minecraftVersionId)
        throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    /**
     * Gets Parchment versions grouped by Minecraft version.
     *
     * @return a future containing the grouped Parchment versions
     */
    public CompletableFuture<Map<String, List<ParchmentVersion>>> getGroupedVersions() {
        return cache.getOrFetch(
            "parchment:grouped",
            SwitchboardClient.MAP_OF_PARCHMENT_VERSIONS,
            VERSIONS_TTL,
            client::fetchGroupedParchmentVersions);
    }

    /**
     * Returns grouped Parchment versions synchronously.
     *
     * @return the grouped Parchment versions
     */
    public Map<String, List<ParchmentVersion>> getGroupedVersionsSync()
        throws ExecutionException, InterruptedException {
        return getGroupedVersions().get();
    }
}
