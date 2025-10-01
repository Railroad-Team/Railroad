package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.pojo.FabricLoaderVersion;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public record FabricLoaderVersionRepository(SwitchboardClient client, CacheManager cache) {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    public CompletableFuture<List<FabricLoaderVersion>> getAllVersions() {
        return cache.getOrFetch(
            "fabric:loader:versions",
            SwitchboardClient.LIST_OF_FABRIC_LOADER_VERSIONS,
            VERSIONS_TTL,
            client::fetchFabricLoaderVersions
        );
    }

    public List<FabricLoaderVersion> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    public CompletableFuture<List<FabricLoaderVersion>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:loader:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_FABRIC_LOADER_VERSIONS,
            VERSIONS_TTL,
            () -> client.fetchFabricLoaderVersions(normalized)
        );
    }

    public List<FabricLoaderVersion> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    public CompletableFuture<FabricLoaderVersion> getLatestVersion() {
        return getLatestVersion(false);
    }

    public CompletableFuture<FabricLoaderVersion> getLatestVersion(boolean includePrereleases) {
        String key = includePrereleases ? "fabric:loader:latest:prereleases" : "fabric:loader:latest";
        return cache.getOrFetch(
            key,
            FabricLoaderVersion.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricLoaderVersion(includePrereleases)
        );
    }

    public FabricLoaderVersion getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    public FabricLoaderVersion getLatestVersionSync(boolean includePrereleases) throws ExecutionException, InterruptedException {
        return getLatestVersion(includePrereleases).get();
    }

    public CompletableFuture<FabricLoaderVersion> getLatestVersionFor(String minecraftVersionId) {
        return getLatestVersionFor(minecraftVersionId, false);
    }

    public CompletableFuture<FabricLoaderVersion> getLatestVersionFor(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:loader:latest:" + normalized + (includePrereleases ? ":prereleases" : "");

        return cache.getOrFetch(
            key,
            FabricLoaderVersion.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricLoaderVersion(normalized, includePrereleases)
        );
    }

    public FabricLoaderVersion getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    public FabricLoaderVersion getLatestVersionForSync(String minecraftVersionId, boolean includePrereleases) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId, includePrereleases).get();
    }
}
