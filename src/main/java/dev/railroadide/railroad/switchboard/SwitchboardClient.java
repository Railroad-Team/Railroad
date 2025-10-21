package dev.railroadide.railroad.switchboard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.core.switchboard.pojo.ParchmentVersion;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.railroad.Railroad;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public record SwitchboardClient(String baseUrl) {
    public static final TypeToken<List<String>> LIST_OF_STRINGS = new TypeToken<>() {};
    public static final TypeToken<List<FabricLoaderVersion>> LIST_OF_FABRIC_LOADER_VERSIONS = new TypeToken<>() {};
    public static final TypeToken<List<ParchmentVersion>> LIST_OF_PARCHMENT_VERSIONS = new TypeToken<>() {};
    public static final TypeToken<Map<String, List<ParchmentVersion>>> MAP_OF_PARCHMENT_VERSIONS = new TypeToken<>() {};

    public SwitchboardClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public CompletableFuture<List<MinecraftVersion>> fetchMinecraftVersions() {
        return getJson("minecraft/versions", new TypeToken<>() {});
    }

    private <T> CompletableFuture<T> getJson(String endpoint, TypeToken<T> clazz) {
        Call call = Railroad.HTTP_CLIENT.newCall(new Request.Builder().get().url(this.baseUrl + endpoint).build());
        CompletableFuture<T> future = new CompletableFuture<>();
        call.enqueue(new JsonCallback<>(future, clazz));
        return future;
    }

    private <T> CompletableFuture<T> getJson(String endpoint, Class<T> clazz) {
        return getJson(endpoint, TypeToken.get(clazz));
    }

    public CompletableFuture<Optional<MinecraftVersion>> fetchMinecraftVersionById(String id) {
        return getJson("minecraft/versions/" + id.toLowerCase(Locale.ROOT), MinecraftVersion.class).thenApply(Optional::of);
    }

    public CompletableFuture<MinecraftVersion> fetchLatestMinecraftVersion() {
        return getJson("minecraft/latest", MinecraftVersion.class);
    }

    public CompletableFuture<MinecraftVersion> fetchLatestMinecraftVersionOfType(MinecraftVersion.Type type) {
        return getJson("minecraft/latest/" + type.name().toLowerCase(Locale.ROOT), MinecraftVersion.class);
    }

    public CompletableFuture<List<String>> fetchForgeVersions() {
        return getJson("forge/versions", LIST_OF_STRINGS);
    }

    public CompletableFuture<List<String>> fetchForgeVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("forge/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    public CompletableFuture<String> fetchLatestForgeVersion() {
        return fetchLatestForgeVersion(false);
    }

    public CompletableFuture<String> fetchLatestForgeVersion(boolean includePrereleases) {
        String endpoint = "forge/latest";
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<String> fetchLatestForgeVersion(String minecraftVersionId) {
        return fetchLatestForgeVersion(minecraftVersionId, false);
    }

    public CompletableFuture<String> fetchLatestForgeVersion(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "forge/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<List<String>> fetchNeoforgeVersions() {
        return getJson("neoforge/versions", LIST_OF_STRINGS);
    }

    public CompletableFuture<List<String>> fetchNeoforgeVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("neoforge/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    public CompletableFuture<String> fetchLatestNeoforgeVersion() {
        return fetchLatestNeoforgeVersion(false);
    }

    public CompletableFuture<String> fetchLatestNeoforgeVersion(boolean includePrereleases) {
        String endpoint = "neoforge/latest";
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<String> fetchLatestNeoforgeVersion(String minecraftVersionId) {
        return fetchLatestNeoforgeVersion(minecraftVersionId, false);
    }

    public CompletableFuture<String> fetchLatestNeoforgeVersion(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "neoforge/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<List<String>> fetchFabricApiVersions() {
        return getJson("fabric/api/versions", LIST_OF_STRINGS);
    }

    public CompletableFuture<List<String>> fetchFabricApiVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("fabric/api/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    public CompletableFuture<String> fetchLatestFabricApiVersion() {
        return fetchLatestFabricApiVersion(false);
    }

    public CompletableFuture<String> fetchLatestFabricApiVersion(boolean includePrereleases) {
        String endpoint = "fabric/api/latest";
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<String> fetchLatestFabricApiVersion(String minecraftVersionId) {
        return fetchLatestFabricApiVersion(minecraftVersionId, false);
    }

    public CompletableFuture<String> fetchLatestFabricApiVersion(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "fabric/api/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<List<FabricLoaderVersion>> fetchFabricLoaderVersions() {
        return getJson("fabric/loader/versions", LIST_OF_FABRIC_LOADER_VERSIONS);
    }

    public CompletableFuture<List<FabricLoaderVersion>> fetchFabricLoaderVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("fabric/loader/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_FABRIC_LOADER_VERSIONS);
    }

    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion() {
        return fetchLatestFabricLoaderVersion(false);
    }

    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion(boolean includePrereleases) {
        String endpoint = "fabric/loader/latest";
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, FabricLoaderVersion.class);
    }

    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion(String minecraftVersionId) {
        return fetchLatestFabricLoaderVersion(minecraftVersionId, false);
    }

    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "fabric/loader/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases)
            endpoint += "?includePrereleases=true";

        return getJson(endpoint, FabricLoaderVersion.class);
    }

    public CompletableFuture<List<String>> fetchYarnVersions() {
        return getJson("yarn/versions", LIST_OF_STRINGS);
    }

    public CompletableFuture<List<String>> fetchYarnVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("yarn/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    public CompletableFuture<String> fetchLatestYarnVersion() {
        return getJson("yarn/latest", VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<String> fetchLatestYarnVersion(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("yarn/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT), VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<List<String>> fetchMcpVersions() {
        return getJson("mcp/versions", LIST_OF_STRINGS);
    }

    public CompletableFuture<List<String>> fetchMcpVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("mcp/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    public CompletableFuture<String> fetchLatestMcpVersion() {
        return getJson("mcp/latest", VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<String> fetchLatestMcpVersion(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("mcp/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT), VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<List<String>> fetchMojmapVersions() {
        return getJson("mojmap/versions", LIST_OF_STRINGS);
    }

    public CompletableFuture<List<String>> fetchMojmapVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("mojmap/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    public CompletableFuture<String> fetchLatestMojmapVersion() {
        return getJson("mojmap/latest", VersionResponse.class).thenApply(VersionResponse::version);
    }

    public CompletableFuture<List<ParchmentVersion>> fetchParchmentVersions() {
        return getJson("parchment/versions", LIST_OF_PARCHMENT_VERSIONS);
    }

    public CompletableFuture<List<ParchmentVersion>> fetchParchmentVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("parchment/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_PARCHMENT_VERSIONS);
    }

    public CompletableFuture<ParchmentVersion> fetchLatestParchmentVersion() {
        return getJson("parchment/latest", ParchmentVersion.class);
    }

    public CompletableFuture<ParchmentVersion> fetchLatestParchmentVersion(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("parchment/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT), ParchmentVersion.class);
    }

    public CompletableFuture<Map<String, List<ParchmentVersion>>> fetchGroupedParchmentVersions() {
        return getJson("parchment/grouped", MAP_OF_PARCHMENT_VERSIONS);
    }

    public record JsonCallback<T>(CompletableFuture<T> future, TypeToken<T> clazz) implements Callback {
        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException exception) {
            future.completeExceptionally(exception);
        }

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) {
            try (response) {
                if (!response.isSuccessful()) {
                    future.completeExceptionally(new RuntimeException("Request failed with code: " + response.code()));
                    return;
                }

                ResponseBody body = Objects.requireNonNull(response.body());
                T result = ServiceLocator.getService(Gson.class).fromJson(body.charStream(), clazz.getType());
                future.complete(result);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        }
    }

    private record VersionResponse(String version) {
    }
}
