package dev.railroadide.railroad.project.minecraft.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.VersionService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FabricLoaderVersionService extends VersionService<FabricLoaderVersionService.FabricLoaderVersion> {
    public static final FabricLoaderVersionService INSTANCE = new FabricLoaderVersionService();

    private static final String LOADER_VERSIONS_URL = "https://meta.fabricmc.net/v2/versions/loader/%s";
    private static final String LOADER_VERSION_URL = "https://meta.fabricmc.net/v2/versions/loader/%s/%s";
    private static final String ALL_LOADER_VERSIONS_URL = "https://meta.fabricmc.net/v2/versions/loader";

    private final ObjectProperty<FabricLoaderVersion> latestCached = new SimpleObjectProperty<>();

    private FabricLoaderVersionService() {
        super("FabricLoader");
    }

    public FabricLoaderVersion latest() {
        FabricLoaderVersion cached = latestCached.get();
        if (cached != null)
            return cached;

        List<MinecraftVersion> minecraftVersions = MinecraftVersion.getVersions();
        for (int i = minecraftVersions.size() - 1; i >= 0; i--) {
            MinecraftVersion minecraftVersion = minecraftVersions.get(i);
            FabricLoaderVersion latestVersion = getLatestVersion(minecraftVersion);
            if (latestVersion != null) {
                latestCached.set(latestVersion);
                return latestVersion;
            }
        }

        return null;
    }

    public FabricLoaderVersion getLatestVersion(MinecraftVersion minecraftVersion) {
        return latestFor(minecraftVersion).orElse(null);
    }

    public Optional<FabricLoaderVersion> findVersion(MinecraftVersion minecraftVersion, String version) {
        if (version == null || version.isEmpty())
            return Optional.empty();

        return listVersionsFor(minecraftVersion, true).stream()
                .filter(loaderVersion -> version.equals(loaderVersion.loaderVersion().version()))
                .findFirst()
                .or(() -> fetchSingleVersion(minecraftVersion, version));
    }

    @Override
    public Optional<FabricLoaderVersion> latestFor(MinecraftVersion minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<FabricLoaderVersion> latestFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        return listVersionsFor(minecraftVersion, includePrereleases).stream().findFirst();
    }

    @Override
    public List<FabricLoaderVersion> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<FabricLoaderVersion> listAllVersions(boolean includePrereleases) {
        List<FabricLoaderVersion> versions = allVersions();
        if (includePrereleases)
            return versions;

        return versions.stream()
                .filter(version -> version.loaderVersion().stable())
                .toList();
    }

    @Override
    public List<FabricLoaderVersion> listVersionsFor(MinecraftVersion minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    // TODO: This doesn't work
    @Override
    public List<FabricLoaderVersion> listVersionsFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        List<FabricLoaderVersion> versions = versionsFor(minecraftVersion);
        if (includePrereleases)
            return versions;

        return versions.stream()
                .filter(version -> version.loaderVersion().stable())
                .toList();
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        try {
            cache.remove("all");
            allVersions();
            MinecraftVersion.getVersions().forEach(version -> cache.remove(cacheKeyFor(version)));
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to refresh Fabric loader versions", exception);
        }
    }

    private List<FabricLoaderVersion> allVersions() {
        CacheEntry<List<FabricLoaderVersion>> entry = cache.get("all");
        if (entry != null && entry.isActive())
            return entry.value();

        try {
            List<FabricLoaderVersion> fresh = fetchAllVersions();
            cache.put("all", new CacheEntry<>(fresh, Instant.now().plus(ttl)));
            return fresh;
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to load Fabric loader versions", exception);
            return entry != null ? entry.value() : List.of();
        }
    }

    private List<FabricLoaderVersion> versionsFor(MinecraftVersion minecraftVersion) {
        String cacheKey = cacheKeyFor(minecraftVersion);
        CacheEntry<List<FabricLoaderVersion>> entry = cache.get(cacheKey);
        if (entry != null && entry.isActive())
            return entry.value();

        try {
            List<FabricLoaderVersion> fresh = fetchVersionsFor(minecraftVersion);
            cache.put(cacheKey, new CacheEntry<>(fresh, Instant.now().plus(ttl)));
            return fresh;
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to load Fabric versions for Minecraft {}", minecraftVersion.id(), exception);
            return entry != null ? entry.value() : List.of();
        }
    }

    private Optional<FabricLoaderVersion> fetchSingleVersion(MinecraftVersion minecraftVersion, String version) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(LOADER_VERSION_URL.formatted(minecraftVersion.id(), version)))
                    .header("User-Agent", userAgent)
                    .timeout(DEFAULT_HTTP_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200)
                return Optional.empty();

            JsonObject versionObject = Railroad.GSON.fromJson(response.body(), JsonObject.class);
            return parse(versionObject);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to load Fabric loader version {} for Minecraft {}", version, minecraftVersion.id(), exception);
            return Optional.empty();
        }
    }

    private List<FabricLoaderVersion> fetchVersionsFor(MinecraftVersion minecraftVersion) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LOADER_VERSIONS_URL.formatted(minecraftVersion.id())))
                .header("User-Agent", userAgent)
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new RuntimeException("Fabric loader versions HTTP " + response.statusCode());

        JsonArray jsonVersions = Railroad.GSON.fromJson(response.body(), JsonArray.class);
        return parseArray(jsonVersions);
    }

    private List<FabricLoaderVersion> fetchAllVersions() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ALL_LOADER_VERSIONS_URL))
                .header("User-Agent", userAgent)
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new RuntimeException("Fabric loader versions HTTP " + response.statusCode());

        JsonArray jsonVersions = Railroad.GSON.fromJson(response.body(), JsonArray.class);
        return parseArray(jsonVersions);
    }

    private List<FabricLoaderVersion> parseArray(JsonArray jsonArray) {
        if (jsonArray == null)
            return List.of();

        List<FabricLoaderVersion> versions = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            if (!element.isJsonObject())
                continue;

            parse(element.getAsJsonObject()).ifPresent(versions::add);
        }

        return List.copyOf(versions);
    }

    private Optional<FabricLoaderVersion> parse(JsonObject versionObject) {
        if (versionObject == null)
            return Optional.empty();

        if (!versionObject.has("loader") || !versionObject.has("intermediary") || !versionObject.has("launcherMeta"))
            return Optional.empty();

        try {
            LoaderVersion loaderVersion = Railroad.GSON.fromJson(versionObject.get("loader"), LoaderVersion.class);
            IntermediaryVersion intermediaryVersion = Railroad.GSON.fromJson(versionObject.get("intermediary"), IntermediaryVersion.class);
            LauncherMeta launcherMeta = Railroad.GSON.fromJson(versionObject.get("launcherMeta"), LauncherMeta.class);
            return Optional.of(new FabricLoaderVersion(loaderVersion, intermediaryVersion, launcherMeta));
        } catch (JsonSyntaxException exception) {
            Railroad.LOGGER.warn("Skipping malformed Fabric loader version entry: {}", versionObject, exception);
            return Optional.empty();
        }
    }

    private static String cacheKeyFor(MinecraftVersion minecraftVersion) {
        return "mc:" + minecraftVersion.id();
    }

    public record FabricLoaderVersion(LoaderVersion loaderVersion,
                                      IntermediaryVersion intermediaryVersion,
                                      LauncherMeta launcherMeta) {
    }

    public record LoaderVersion(String separator, int build, String maven, String version, boolean stable) {
    }

    public record IntermediaryVersion(String version, String maven, boolean stable) {
    }

    public record LauncherMeta(String version, @SerializedName("min_java_version") int minJavaVersion,
                               Libraries libraries) {
        public record Libraries(List<Library> client, List<Library> common, List<Library> server,
                                List<Library> development) {
            public record Library(String name, String url, String md5, String sha1, String sha256, String sha512,
                                  int size) {
            }
        }
    }
}
