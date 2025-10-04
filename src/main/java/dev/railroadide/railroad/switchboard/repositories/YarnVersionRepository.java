package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.core.switchboard.SwitchboardRepository;
import dev.railroadide.core.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.SwitchboardClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public record YarnVersionRepository(SwitchboardClient client, CacheManager cache)
        implements SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    public CompletableFuture<List<String>> getAllVersions() {
        return cache.getOrFetch(
            "yarn:versions",
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            client::fetchYarnVersions
        );
    }

    public List<String> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    public CompletableFuture<List<String>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "yarn:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            () -> client.fetchYarnVersions(normalized)
        );
    }

    public List<String> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    public CompletableFuture<String> getLatestVersion() {
        return cache.getOrFetch(
            "yarn:latest",
            String.class,
            LATEST_TTL,
            client::fetchLatestYarnVersion
        );
    }

    public String getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "yarn:latest:" + normalized;

        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestYarnVersion(normalized)
        );
    }

    public String getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }
}
