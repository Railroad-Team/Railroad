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
 * Cached access to MCP versions provided by Switchboard.
 *
 * @param client the Switchboard HTTP client
 * @param cache the cache used for version metadata
 */
public record McpVersionRepository(SwitchboardClient client, CacheManager cache)
    implements
        SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    /**
     * Returns all MCP versions.
     *
     * @return a future containing all MCP versions
     */
    public CompletableFuture<List<String>> getAllVersions() {
        return cache.getOrFetch(
            "mcp:versions",
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            client::fetchMcpVersions);
    }

    /**
     * Returns all MCP versions synchronously.
     *
     * @return all MCP versions
     */
    public List<String> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    /**
     * Gets MCP versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible MCP versions
     */
    public CompletableFuture<List<String>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "mcp:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            () -> client.fetchMcpVersions(normalized));
    }

    /**
     * Gets compatible MCP versions synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the compatible MCP versions
     */
    public List<String> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    /**
     * Returns the latest MCP version.
     *
     * @return a future containing the latest MCP version
     */
    public CompletableFuture<String> getLatestVersion() {
        return cache.getOrFetch(
            "mcp:latest",
            String.class,
            LATEST_TTL,
            client::fetchLatestMcpVersion);
    }

    /**
     * Returns the latest MCP version synchronously.
     *
     * @return the latest MCP version
     */
    public String getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    /**
     * Gets the latest MCP version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible MCP version
     */
    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "mcp:latest:" + normalized;

        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestMcpVersion(normalized));
    }

    /**
     * Gets the latest MCP version compatible with a Minecraft version synchronously.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return the latest compatible MCP version
     */
    public String getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }
}
