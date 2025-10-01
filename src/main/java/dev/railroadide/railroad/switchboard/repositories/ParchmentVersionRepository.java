package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.railroad.switchboard.SwitchboardClient;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.pojo.ParchmentVersion;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public record ParchmentVersionRepository(SwitchboardClient client, CacheManager cache) {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    public CompletableFuture<List<ParchmentVersion>> getAllVersions() {
        return cache.getOrFetch(
            "parchment:versions",
            SwitchboardClient.LIST_OF_PARCHMENT_VERSIONS,
            VERSIONS_TTL,
            client::fetchParchmentVersions
        );
    }

    public List<ParchmentVersion> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    public CompletableFuture<List<ParchmentVersion>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "parchment:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_PARCHMENT_VERSIONS,
            VERSIONS_TTL,
            () -> client.fetchParchmentVersions(normalized)
        );
    }

    public List<ParchmentVersion> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    public CompletableFuture<ParchmentVersion> getLatestVersion() {
        return cache.getOrFetch(
            "parchment:latest",
            ParchmentVersion.class,
            LATEST_TTL,
            client::fetchLatestParchmentVersion
        );
    }

    public ParchmentVersion getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    public CompletableFuture<ParchmentVersion> getLatestVersionFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "parchment:latest:" + normalized;

        return cache.getOrFetch(
            key,
            ParchmentVersion.class,
            LATEST_TTL,
            () -> client.fetchLatestParchmentVersion(normalized)
        );
    }

    public ParchmentVersion getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    public CompletableFuture<Map<String, List<ParchmentVersion>>> getGroupedVersions() {
        return cache.getOrFetch(
            "parchment:grouped",
            SwitchboardClient.MAP_OF_PARCHMENT_VERSIONS,
            VERSIONS_TTL,
            client::fetchGroupedParchmentVersions
        );
    }

    public Map<String, List<ParchmentVersion>> getGroupedVersionsSync() throws ExecutionException, InterruptedException {
        return getGroupedVersions().get();
    }
}
