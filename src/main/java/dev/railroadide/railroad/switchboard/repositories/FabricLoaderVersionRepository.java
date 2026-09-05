package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.SwitchboardRepository;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.pojo.FabricLoaderVersion;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Cached access to Fabric Loader versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record FabricLoaderVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    /**
     * Returns all Fabric Loader versions.
     *
     * @return a future containing all Fabric Loader versions
     */
    public CompletableFuture<List<FabricLoaderVersion>> getAllVersions() {
        return cache.getOrFetch(
            "fabric:loader:versions",
            SwitchboardClient.LIST_OF_FABRIC_LOADER_VERSIONS,
            VERSIONS_TTL,
            client::fetchFabricLoaderVersions);
    }

    /**
     * Returns all Fabric Loader versions synchronously.
     *
     * @return all Fabric Loader versions
     */
    public List<FabricLoaderVersion> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets Fabric Loader versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Fabric Loader versions
     */
    public CompletableFuture<List<FabricLoaderVersion>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:loader:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_FABRIC_LOADER_VERSIONS,
            VERSIONS_TTL,
            () -> client.fetchFabricLoaderVersions(normalized));
    }

    /**
     * Gets compatible Fabric Loader versions synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the compatible Fabric Loader versions
     */
    public List<FabricLoaderVersion> getVersionsForSync(String minecraftVersionId)
        throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    /**
     * Returns the latest stable Fabric Loader version.
     *
     * @return a future containing the latest stable Fabric Loader version
     */
    public CompletableFuture<FabricLoaderVersion> getLatestVersion() {
        return getLatestVersion(false);
    }

    /**
     * Gets the latest Fabric Loader version.
     *
     * @param includePrereleases whether prereleases may be returned
     * @return a future containing the latest Fabric Loader version
     */
    public CompletableFuture<FabricLoaderVersion> getLatestVersion(boolean includePrereleases) {
        String key = includePrereleases ? "fabric:loader:latest:prereleases" : "fabric:loader:latest";
        return cache.getOrFetch(
            key,
            FabricLoaderVersion.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricLoaderVersion(includePrereleases));
    }

    /**
     * Returns the latest stable Fabric Loader version synchronously.
     *
     * @return the latest stable Fabric Loader version
     */
    public FabricLoaderVersion getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest Fabric Loader version synchronously.
     *
     * @param includePrereleases whether prereleases may be returned
     * @return the latest Fabric Loader version
     */
    public FabricLoaderVersion getLatestVersionSync(boolean includePrereleases)
        throws ExecutionException, InterruptedException {
        return getLatestVersion(includePrereleases).get();
    }

    /**
     * Gets the latest stable Fabric Loader version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible version
     */
    public CompletableFuture<FabricLoaderVersion> getLatestVersionFor(String minecraftVersionId) {
        return getLatestVersionFor(minecraftVersionId, false);
    }

    /**
     * Gets the latest Fabric Loader version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prereleases may be returned
     * @return a future containing the latest compatible version
     */
    public CompletableFuture<FabricLoaderVersion> getLatestVersionFor(
        String minecraftVersionId,
        boolean includePrereleases
    ) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:loader:latest:" + normalized + (includePrereleases ? ":prereleases" : "");

        return cache.getOrFetch(
            key,
            FabricLoaderVersion.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricLoaderVersion(normalized, includePrereleases))
            .thenApply(fabricLoaderVersion -> fabricLoaderVersion.version() == null ? null : fabricLoaderVersion);
    }

    /**
     * Gets the latest stable Fabric Loader version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the latest compatible version
     */
    public FabricLoaderVersion getLatestVersionForSync(String minecraftVersionId)
        throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    /**
     * Gets the latest Fabric Loader version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prereleases may be returned
     * @return the latest compatible version
     */
    public FabricLoaderVersion getLatestVersionForSync(String minecraftVersionId, boolean includePrereleases)
        throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId, includePrereleases).get();
    }
}
