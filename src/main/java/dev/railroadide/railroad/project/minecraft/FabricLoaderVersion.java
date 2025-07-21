package dev.railroadide.railroad.project.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import dev.railroadide.railroad.Railroad;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record FabricLoaderVersion(LoaderVersion loaderVersion, IntermediaryVersion intermediaryVersion,
                                  LauncherMeta launcherMeta) {
    private static final ObservableMap<MinecraftVersion, ObservableList<FabricLoaderVersion>> VERSIONS = FXCollections.observableHashMap();
    private static final ObjectProperty<FabricLoaderVersion> LATEST = new SimpleObjectProperty<>();
    private static final String LOADER_VERSIONS_URL = "https://meta.fabricmc.net/v2/versions/loader/%s";
    private static final String LOADER_VERSION_URL = "https://meta.fabricmc.net/v2/versions/loader/%s/%s";

    public static List<FabricLoaderVersion> getVersions(MinecraftVersion minecraftVersion) {
        if (VERSIONS.containsKey(minecraftVersion))
            return VERSIONS.get(minecraftVersion);

        try {
            List<FabricLoaderVersion> versions = new ArrayList<>();

            JsonArray jsonVersions = Railroad.GSON.fromJson(new InputStreamReader(new URI(LOADER_VERSIONS_URL.formatted(minecraftVersion.id())).toURL().openStream(), StandardCharsets.UTF_8), JsonArray.class);
            for (JsonElement versionElement : jsonVersions) {
                if (!versionElement.isJsonObject())
                    continue;

                JsonObject versionObject = versionElement.getAsJsonObject();
                if (!versionObject.has("loader") || !versionObject.has("intermediary") || !versionObject.has("launcherMeta"))
                    continue;

                LoaderVersion loaderVersion = Railroad.GSON.fromJson(versionObject.get("loader"), LoaderVersion.class);
                IntermediaryVersion intermediaryVersion = Railroad.GSON.fromJson(versionObject.get("intermediary"), IntermediaryVersion.class);
                LauncherMeta launcherMeta = Railroad.GSON.fromJson(versionObject.get("launcherMeta"), LauncherMeta.class);
                var fabricVersion = new FabricLoaderVersion(loaderVersion, intermediaryVersion, launcherMeta);
                versions.add(fabricVersion);
                VERSIONS.computeIfAbsent(minecraftVersion, key -> FXCollections.observableArrayList()).add(fabricVersion);
            }

            return versions;
        } catch (IOException | URISyntaxException exception) {
            Railroad.LOGGER.error("Failed to load Fabric versions for Minecraft {}", minecraftVersion.id(), exception);
            return List.of();
        }
    }

    public static FabricLoaderVersion latest() {
        if (LATEST.getValue() != null)
            return LATEST.getValue();

        List<MinecraftVersion> minecraftVersions = MinecraftVersion.getVersions();
        for (int i = minecraftVersions.size() - 1; i >= 0; i--) {
            MinecraftVersion minecraftVersion = minecraftVersions.get(i);
            FabricLoaderVersion latestVersion = getLatestVersion(minecraftVersion);
            if (latestVersion != null) {
                LATEST.setValue(latestVersion);
                return latestVersion;
            }
        }

        return null;
    }

    public static FabricLoaderVersion getLatestVersion(MinecraftVersion minecraftVersion) {
        return getVersions(minecraftVersion).stream().findFirst().orElse(null);
    }

    public static Optional<FabricLoaderVersion> fromId(MinecraftVersion minecraftVersion, String version) {
        if (version == null || version.isEmpty())
            return Optional.empty();

        for (Map.Entry<MinecraftVersion, ObservableList<FabricLoaderVersion>> entry : VERSIONS.entrySet()) {
            if (entry.getKey().equals(minecraftVersion)) {
                for (FabricLoaderVersion fabricLoaderVersion : entry.getValue()) {
                    if (fabricLoaderVersion.loaderVersion().version().equals(version))
                        return Optional.of(fabricLoaderVersion);
                }
            }
        }

        try {
            JsonObject versionObject = Railroad.GSON.fromJson(new InputStreamReader(new URI(LOADER_VERSION_URL.formatted(minecraftVersion.id(), version)).toURL().openStream(), StandardCharsets.UTF_8), JsonObject.class);
            if (!versionObject.has("loader") || !versionObject.has("intermediary") || !versionObject.has("launcherMeta"))
                return Optional.empty();

            LoaderVersion loaderVersion = Railroad.GSON.fromJson(versionObject.get("loader"), LoaderVersion.class);
            IntermediaryVersion intermediaryVersion = Railroad.GSON.fromJson(versionObject.get("intermediary"), IntermediaryVersion.class);
            LauncherMeta launcherMeta = Railroad.GSON.fromJson(versionObject.get("launcherMeta"), LauncherMeta.class);
            return Optional.of(new FabricLoaderVersion(loaderVersion, intermediaryVersion, launcherMeta));
        } catch (IOException | URISyntaxException exception) {
            Railroad.LOGGER.error("Failed to load Fabric version {} for Minecraft {}", version, minecraftVersion.id(), exception);
            return Optional.empty();
        }
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
