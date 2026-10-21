package dev.railroadide.railroad.switchboard;

import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.railroad.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.switchboard.pojo.ParchmentVersion;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for retrieving Minecraft and mod-loader metadata from Switchboard.
 *
 * @param baseUrl the normalized base URL of the Switchboard service
 */
public record SwitchboardClient(String baseUrl) {
    /** Type token for a JSON list of version strings. */
    public static final TypeToken<List<String>> LIST_OF_STRINGS = new TypeToken<>() {
    };
    /** Type token for a JSON list of Fabric Loader versions. */
    public static final TypeToken<List<FabricLoaderVersion>> LIST_OF_FABRIC_LOADER_VERSIONS = new TypeToken<>() {
    };
    /** Type token for a JSON list of Parchment versions. */
    public static final TypeToken<List<ParchmentVersion>> LIST_OF_PARCHMENT_VERSIONS = new TypeToken<>() {
    };
    /** Type token for Parchment versions grouped by Minecraft version. */
    public static final TypeToken<Map<String, List<ParchmentVersion>>> MAP_OF_PARCHMENT_VERSIONS = new TypeToken<>() {
    };

    /**
     * Creates a client for a Switchboard service.
     *
     * @param baseUrl the service base URL, with or without a trailing slash
     */
    public SwitchboardClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /**
     * Fetches all known Minecraft versions.
     *
     * @return a future containing the available Minecraft versions
     */
    public CompletableFuture<List<MinecraftVersion>> fetchMinecraftVersions() {
        return getJson("minecraft/versions", new TypeToken<>() {
        });
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

    /**
     * Fetches a Minecraft version by its identifier.
     *
     * @param id the Minecraft version identifier
     * @return a future containing the matching version, or empty when it is unavailable
     */
    public CompletableFuture<Optional<MinecraftVersion>> fetchMinecraftVersionById(String id) {
        return getJson("minecraft/versions/" + id.toLowerCase(Locale.ROOT), MinecraftVersion.class)
            .thenApply(Optional::of);
    }

    /**
     * Fetches the latest Minecraft version of any type.
     *
     * @return a future containing the latest Minecraft version
     */
    public CompletableFuture<MinecraftVersion> fetchLatestMinecraftVersion() {
        return getJson("minecraft/latest", MinecraftVersion.class);
    }

    /**
     * Fetches the latest Minecraft version of the requested type.
     *
     * @param type the Minecraft release type to fetch
     * @return a future containing the latest version of that type
     */
    public CompletableFuture<MinecraftVersion> fetchLatestMinecraftVersionOfType(MinecraftVersion.Type type) {
        return getJson("minecraft/latest/" + type.name().toLowerCase(Locale.ROOT), MinecraftVersion.class);
    }

    /**
     * Fetches all known Forge versions.
     *
     * @return a future containing Forge version identifiers
     */
    public CompletableFuture<List<String>> fetchForgeVersions() {
        return getJson("forge/versions", LIST_OF_STRINGS);
    }

    /**
     * Fetches Forge versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Forge version identifiers
     */
    public CompletableFuture<List<String>> fetchForgeVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("forge/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    /**
     * Fetches the latest stable Forge version.
     *
     * @return a future containing the latest Forge version identifier
     */
    public CompletableFuture<String> fetchLatestForgeVersion() {
        return fetchLatestForgeVersion(false);
    }

    /**
     * Fetches the latest Forge version, optionally including prereleases.
     *
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest Forge version identifier
     */
    public CompletableFuture<String> fetchLatestForgeVersion(boolean includePrereleases) {
        String endpoint = "forge/latest";
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches the latest stable Forge version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Forge version identifier
     */
    public CompletableFuture<String> fetchLatestForgeVersion(String minecraftVersionId) {
        return fetchLatestForgeVersion(minecraftVersionId, false);
    }

    /**
     * Fetches the latest Forge version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest compatible Forge version identifier
     */
    public CompletableFuture<String> fetchLatestForgeVersion(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "forge/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches all known NeoForge versions.
     *
     * @return a future containing NeoForge version identifiers
     */
    public CompletableFuture<List<String>> fetchNeoforgeVersions() {
        return getJson("neoforge/versions", LIST_OF_STRINGS);
    }

    /**
     * Fetches NeoForge versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible NeoForge version identifiers
     */
    public CompletableFuture<List<String>> fetchNeoforgeVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("neoforge/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    /**
     * Fetches the latest stable NeoForge version.
     *
     * @return a future containing the latest NeoForge version identifier
     */
    public CompletableFuture<String> fetchLatestNeoforgeVersion() {
        return fetchLatestNeoforgeVersion(false);
    }

    /**
     * Fetches the latest NeoForge version, optionally including prereleases.
     *
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest NeoForge version identifier
     */
    public CompletableFuture<String> fetchLatestNeoforgeVersion(boolean includePrereleases) {
        String endpoint = "neoforge/latest";
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches the latest stable NeoForge version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible NeoForge version identifier
     */
    public CompletableFuture<String> fetchLatestNeoforgeVersion(String minecraftVersionId) {
        return fetchLatestNeoforgeVersion(minecraftVersionId, false);
    }

    /**
     * Fetches the latest NeoForge version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest compatible NeoForge version identifier
     */
    public CompletableFuture<String> fetchLatestNeoforgeVersion(String minecraftVersionId, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "neoforge/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches all known Fabric API versions.
     *
     * @return a future containing Fabric API version identifiers
     */
    public CompletableFuture<List<String>> fetchFabricApiVersions() {
        return getJson("fabric/api/versions", LIST_OF_STRINGS);
    }

    /**
     * Fetches Fabric API versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Fabric API version identifiers
     */
    public CompletableFuture<List<String>> fetchFabricApiVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("fabric/api/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    /**
     * Fetches the latest stable Fabric API version.
     *
     * @return a future containing the latest Fabric API version identifier
     */
    public CompletableFuture<String> fetchLatestFabricApiVersion() {
        return fetchLatestFabricApiVersion(false);
    }

    /**
     * Fetches the latest Fabric API version, optionally including prereleases.
     *
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest Fabric API version identifier
     */
    public CompletableFuture<String> fetchLatestFabricApiVersion(boolean includePrereleases) {
        String endpoint = "fabric/api/latest";
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches the latest stable Fabric API version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Fabric API version identifier
     */
    public CompletableFuture<String> fetchLatestFabricApiVersion(String minecraftVersionId) {
        return fetchLatestFabricApiVersion(minecraftVersionId, false);
    }

    /**
     * Fetches the latest Fabric API version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest compatible Fabric API version identifier
     */
    public CompletableFuture<String> fetchLatestFabricApiVersion(
        String minecraftVersionId,
        boolean includePrereleases
    ) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "fabric/api/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches all known Fabric Loader versions.
     *
     * @return a future containing Fabric Loader versions
     */
    public CompletableFuture<List<FabricLoaderVersion>> fetchFabricLoaderVersions() {
        return getJson("fabric/loader/versions", LIST_OF_FABRIC_LOADER_VERSIONS);
    }

    /**
     * Fetches Fabric Loader versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Fabric Loader versions
     */
    public CompletableFuture<List<FabricLoaderVersion>> fetchFabricLoaderVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("fabric/loader/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT),
            LIST_OF_FABRIC_LOADER_VERSIONS);
    }

    /**
     * Fetches the latest stable Fabric Loader version.
     *
     * @return a future containing the latest Fabric Loader version
     */
    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion() {
        return fetchLatestFabricLoaderVersion(false);
    }

    /**
     * Fetches the latest Fabric Loader version, optionally including prereleases.
     *
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest Fabric Loader version
     */
    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion(boolean includePrereleases) {
        String endpoint = "fabric/loader/latest";
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, FabricLoaderVersion.class);
    }

    /**
     * Fetches the latest stable Fabric Loader version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Fabric Loader version
     */
    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion(String minecraftVersionId) {
        return fetchLatestFabricLoaderVersion(minecraftVersionId, false);
    }

    /**
     * Fetches the latest Fabric Loader version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @param includePrereleases whether prerelease versions may be returned
     * @return a future containing the latest compatible Fabric Loader version
     */
    public CompletableFuture<FabricLoaderVersion> fetchLatestFabricLoaderVersion(
        String minecraftVersionId,
        boolean includePrereleases
    ) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        String endpoint = "fabric/loader/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT);
        if (includePrereleases) {
            endpoint += "?includePrereleases=true";
        }

        return getJson(endpoint, FabricLoaderVersion.class);
    }

    /**
     * Fetches all known Yarn versions.
     *
     * @return a future containing Yarn version identifiers
     */
    public CompletableFuture<List<String>> fetchYarnVersions() {
        return getJson("yarn/versions", LIST_OF_STRINGS);
    }

    /**
     * Fetches Yarn versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Yarn version identifiers
     */
    public CompletableFuture<List<String>> fetchYarnVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("yarn/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    /**
     * Fetches the latest Yarn version.
     *
     * @return a future containing the latest Yarn version identifier
     */
    public CompletableFuture<String> fetchLatestYarnVersion() {
        return getJson("yarn/latest", VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches the latest Yarn version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Yarn version identifier
     */
    public CompletableFuture<String> fetchLatestYarnVersion(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("yarn/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT), VersionResponse.class)
            .thenApply(VersionResponse::version);
    }

    /**
     * Fetches all known MCP versions.
     *
     * @return a future containing MCP version identifiers
     */
    public CompletableFuture<List<String>> fetchMcpVersions() {
        return getJson("mcp/versions", LIST_OF_STRINGS);
    }

    /**
     * Fetches MCP versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible MCP version identifiers
     */
    public CompletableFuture<List<String>> fetchMcpVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("mcp/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    /**
     * Fetches the latest MCP version.
     *
     * @return a future containing the latest MCP version identifier
     */
    public CompletableFuture<String> fetchLatestMcpVersion() {
        return getJson("mcp/latest", VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches the latest MCP version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible MCP version identifier
     */
    public CompletableFuture<String> fetchLatestMcpVersion(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("mcp/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT), VersionResponse.class)
            .thenApply(VersionResponse::version);
    }

    /**
     * Fetches all known Mojmap versions.
     *
     * @return a future containing Mojmap version identifiers
     */
    public CompletableFuture<List<String>> fetchMojmapVersions() {
        return getJson("mojmap/versions", LIST_OF_STRINGS);
    }

    /**
     * Fetches Mojmap versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Mojmap version identifiers
     */
    public CompletableFuture<List<String>> fetchMojmapVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("mojmap/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_STRINGS);
    }

    /**
     * Fetches the latest Mojmap version.
     *
     * @return a future containing the latest Mojmap version identifier
     */
    public CompletableFuture<String> fetchLatestMojmapVersion() {
        return getJson("mojmap/latest", VersionResponse.class).thenApply(VersionResponse::version);
    }

    /**
     * Fetches all known Parchment versions.
     *
     * @return a future containing Parchment versions
     */
    public CompletableFuture<List<ParchmentVersion>> fetchParchmentVersions() {
        return getJson("parchment/versions", LIST_OF_PARCHMENT_VERSIONS);
    }

    /**
     * Fetches Parchment versions compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing compatible Parchment versions
     */
    public CompletableFuture<List<ParchmentVersion>> fetchParchmentVersions(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("parchment/versions/" + minecraftVersionId.toLowerCase(Locale.ROOT), LIST_OF_PARCHMENT_VERSIONS);
    }

    /**
     * Fetches the latest Parchment version.
     *
     * @return a future containing the latest Parchment version
     */
    public CompletableFuture<ParchmentVersion> fetchLatestParchmentVersion() {
        return getJson("parchment/latest", ParchmentVersion.class);
    }

    /**
     * Fetches the latest Parchment version compatible with a Minecraft version.
     *
     * @param minecraftVersionId the Minecraft version identifier
     * @return a future containing the latest compatible Parchment version
     */
    public CompletableFuture<ParchmentVersion> fetchLatestParchmentVersion(String minecraftVersionId) {
        Objects.requireNonNull(minecraftVersionId, "minecraftVersionId");
        return getJson("parchment/latest/" + minecraftVersionId.toLowerCase(Locale.ROOT), ParchmentVersion.class);
    }

    /**
     * Fetches Parchment versions grouped by Minecraft version.
     *
     * @return a future containing the grouped Parchment versions
     */
    public CompletableFuture<Map<String, List<ParchmentVersion>>> fetchGroupedParchmentVersions() {
        return getJson("parchment/grouped", MAP_OF_PARCHMENT_VERSIONS);
    }

    /**
     * Completes a future from an asynchronous OkHttp JSON response.
     *
     * @param <T> the response value type
     * @param future the future to complete
     * @param clazz the JSON type token used for deserialization
     */
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
                T result = Railroad.GSON.fromJson(body.charStream(), clazz.getType());
                future.complete(result);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        }
    }

    private record VersionResponse(String version) {
    }
}
