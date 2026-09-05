package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.SwitchboardRepository;
import dev.railroadide.railroad.switchboard.cache.CacheManager;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Cached access to Fabric API versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record FabricApiVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    /**
     * Returns all Fabric API versions.
     *
     * @return a future containing all Fabric API versions
     */
    public CompletableFuture<List<String>> getAllVersions() {
        return cache.getOrFetch(
            "fabric:api:versions",
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            client::fetchFabricApiVersions);
    }

    /**
     * Returns all Fabric API versions synchronously.
     *
     * @return all Fabric API versions
     */
    public List<String> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets Fabric API versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Fabric API versions
     */
    public CompletableFuture<List<String>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:api:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            () -> client.fetchFabricApiVersions(normalized));
    }

    /**
     * Gets compatible Fabric API versions synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the compatible Fabric API versions
     */
    public List<String> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    /**
     * Returns the latest stable Fabric API version.
     *
     * @return a future containing the latest stable Fabric API version
     */
    public CompletableFuture<String> getLatestVersion() {
        return getLatestVersion(false);
    }

    /**
     * Gets the latest Fabric API version.
     *
     * @param includePrereleases whether prereleases may be returned
     * @return a future containing the latest Fabric API version
     */
    public CompletableFuture<String> getLatestVersion(boolean includePrereleases) {
        String key = includePrereleases ? "fabric:api:latest:prereleases" : "fabric:api:latest";
        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricApiVersion(includePrereleases));
    }

    /**
     * Returns the latest stable Fabric API version synchronously.
     *
     * @return the latest stable Fabric API version
     */
    public String getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest Fabric API version synchronously.
     *
     * @param includePrereleases whether prereleases may be returned
     * @return the latest Fabric API version
     */
    public String getLatestVersionSync(boolean includePrereleases) throws ExecutionException, InterruptedException {
        return getLatestVersion(includePrereleases).get();
    }

    /**
     * Gets the latest stable Fabric API version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible version
     */
    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId) {
        return getLatestVersionFor(minecraftVersionId, false);
    }

    /**
     * Gets the latest Fabric API version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prereleases may be returned
     * @return a future containing the latest compatible version
     */
    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:api:latest:" + normalized + (includePrereleases ? ":prereleases" : "");

        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricApiVersion(normalized, includePrereleases));
    }

    /**
     * Gets the latest stable Fabric API version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the latest compatible version
     */
    public String getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    /**
     * Gets the latest Fabric API version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prereleases may be returned
     * @return the latest compatible version
     */
    public String getLatestVersionForSync(String minecraftVersionId, boolean includePrereleases)
        throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId, includePrereleases).get();
    }

    /**
     * Extracts the Minecraft version suffix from a Fabric API version.
     *
     * @param fabricApiVersion the Fabric API version to inspect
     * @return the extracted Minecraft version, or empty when it cannot be determined
     */
    public static Optional<String> fapiToMinecraftVersion(String fabricApiVersion) {
        int plus = fabricApiVersion.indexOf('+');
        if (plus < 0 || plus == fabricApiVersion.length() - 1)
            return Optional.empty();

        String possibleVersion = fabricApiVersion.substring(plus + 1);
        if (possibleVersion.contains("build."))
            return Optional.empty(); // TODO: Handle this by figuring out what build versions are for

        if (possibleVersion.endsWith("_experimental")) {
            possibleVersion = possibleVersion.substring(0, possibleVersion.length() - "_experimental".length());
        }

        return Optional.of(possibleVersion);
    }
}
