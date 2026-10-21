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
 * Cached access to NeoForge versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record NeoforgeVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    /**
     * Returns all NeoForge versions.
     *
     * @return a future containing all NeoForge versions
     */
    public CompletableFuture<List<String>> getAllVersions() {
        return cache.getOrFetch(
            "neoforge:versions",
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            client::fetchNeoforgeVersions);
    }

    /**
     * Returns all NeoForge versions synchronously.
     *
     * @return all NeoForge versions
     */
    public List<String> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets NeoForge versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible NeoForge versions
     */
    public CompletableFuture<List<String>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "neoforge:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            () -> client.fetchNeoforgeVersions(normalized));
    }

    /**
     * Gets compatible NeoForge versions synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the compatible NeoForge versions
     */
    public List<String> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    /**
     * Returns the latest stable NeoForge version.
     *
     * @return a future containing the latest stable NeoForge version
     */
    public CompletableFuture<String> getLatestVersion() {
        return getLatestVersion(false);
    }

    /**
     * Gets the latest NeoForge version.
     *
     * @param includePrereleases whether prereleases may be returned
     * @return a future containing the latest NeoForge version
     */
    public CompletableFuture<String> getLatestVersion(boolean includePrereleases) {
        String key = includePrereleases ? "neoforge:latest:prereleases" : "neoforge:latest";
        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestNeoforgeVersion(includePrereleases));
    }

    /**
     * Returns the latest stable NeoForge version synchronously.
     *
     * @return the latest stable NeoForge version
     */
    public String getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest NeoForge version synchronously.
     *
     * @param includePrereleases whether prereleases may be returned
     * @return the latest NeoForge version
     */
    public String getLatestVersionSync(boolean includePrereleases) throws ExecutionException, InterruptedException {
        return getLatestVersion(includePrereleases).get();
    }

    /**
     * Gets the latest stable NeoForge version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible version
     */
    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId) {
        return getLatestVersionFor(minecraftVersionId, false);
    }

    /**
     * Gets the latest NeoForge version for a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prereleases may be returned
     * @return a future containing the latest compatible version
     */
    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "neoforge:latest:" + normalized + (includePrereleases ? ":prereleases" : "");

        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestNeoforgeVersion(normalized, includePrereleases));
    }

    /**
     * Gets the latest stable NeoForge version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the latest compatible version
     */
    public String getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    /**
     * Gets the latest NeoForge version for a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prereleases may be returned
     * @return the latest compatible version
     */
    public String getLatestVersionForSync(String minecraftVersionId, boolean includePrereleases)
        throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId, includePrereleases).get();
    }
}
