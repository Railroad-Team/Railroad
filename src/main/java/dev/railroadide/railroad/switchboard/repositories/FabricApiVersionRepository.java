package dev.railroadide.railroad.switchboard.repositories;

import dev.railroadide.core.switchboard.SwitchboardRepository;
import dev.railroadide.core.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.SwitchboardClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public record FabricApiVersionRepository(SwitchboardClient client, CacheManager cache)
    implements SwitchboardRepository {
    private static final Duration VERSIONS_TTL = Duration.ofHours(12);
    private static final Duration LATEST_TTL = Duration.ofHours(1);

    public CompletableFuture<List<String>> getAllVersions() {
        return cache.getOrFetch(
            "fabric:api:versions",
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            client::fetchFabricApiVersions
        );
    }

    public List<String> getAllVersionsSync() throws ExecutionException, InterruptedException {
        return getAllVersions().get();
    }

    public CompletableFuture<List<String>> getVersionsFor(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:api:versions:" + normalized;

        return cache.getOrFetch(
            key,
            SwitchboardClient.LIST_OF_STRINGS,
            VERSIONS_TTL,
            () -> client.fetchFabricApiVersions(normalized)
        );
    }

    public List<String> getVersionsForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getVersionsFor(minecraftVersionId).get();
    }

    public CompletableFuture<String> getLatestVersion() {
        return getLatestVersion(false);
    }

    public CompletableFuture<String> getLatestVersion(boolean includePrereleases) {
        String key = includePrereleases ? "fabric:api:latest:prereleases" : "fabric:api:latest";
        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricApiVersion(includePrereleases)
        );
    }

    public String getLatestVersionSync() throws ExecutionException, InterruptedException {
        return getLatestVersion().get();
    }

    public String getLatestVersionSync(boolean includePrereleases) throws ExecutionException, InterruptedException {
        return getLatestVersion(includePrereleases).get();
    }

    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId) {
        return getLatestVersionFor(minecraftVersionId, false);
    }

    public CompletableFuture<String> getLatestVersionFor(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String normalized = minecraftVersionId.toLowerCase(Locale.ROOT);
        String key = "fabric:api:latest:" + normalized + (includePrereleases ? ":prereleases" : "");

        return cache.getOrFetch(
            key,
            String.class,
            LATEST_TTL,
            () -> client.fetchLatestFabricApiVersion(normalized, includePrereleases)
        );
    }

    public String getLatestVersionForSync(String minecraftVersionId) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId).get();
    }

    public String getLatestVersionForSync(String minecraftVersionId, boolean includePrereleases) throws ExecutionException, InterruptedException {
        return getLatestVersionFor(minecraftVersionId, includePrereleases).get();
    }

    public static Optional<String> fapiToMinecraftVersion(String fabricApiVersion) {
        int plus = fabricApiVersion.indexOf('+');
        if (plus < 0 || plus == fabricApiVersion.length() - 1)
            return Optional.empty();

        String possibleVersion = fabricApiVersion.substring(plus + 1);
        if (possibleVersion.contains("build."))
            return Optional.empty(); // TODO: Handle this by figuring out what build versions are for

        if (possibleVersion.endsWith("_experimental"))
            possibleVersion = possibleVersion.substring(0, possibleVersion.length() - "_experimental".length());

        return Optional.of(possibleVersion);
    }
}
