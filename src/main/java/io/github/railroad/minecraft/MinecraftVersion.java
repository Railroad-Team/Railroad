package io.github.railroad.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.project.ProjectType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record MinecraftVersion(String id, VersionType type, String url, LocalDateTime time, LocalDateTime releaseTime) {
    private static final String MINECRAFT_VERSIONS_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private static final ObservableList<MinecraftVersion> MINECRAFT_VERSIONS = FXCollections.observableArrayList();
    private static final ObjectProperty<MinecraftVersion> LATEST_STABLE = new SimpleObjectProperty<>();
    private static final ObjectProperty<MinecraftVersion> LATEST_SNAPSHOT = new SimpleObjectProperty<>();

    public static void load() {
        requestMinecraftVersions();
    }

    private static void requestMinecraftVersions() {
        Railroad.HTTP_CLIENT.newCall(new Request.Builder().url(MINECRAFT_VERSIONS_URL).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException exception) {
                throw new RuntimeException("Failed to request Minecraft versions", exception);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                List<MinecraftVersion> versions = new ArrayList<>();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to request Minecraft versions: " + response.message());
                }

                try {
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new RuntimeException("Failed to request Minecraft versions: Empty response body");
                    }

                    String json = body.string();
                    if (json.isBlank()) {
                        throw new RuntimeException("Failed to request Minecraft versions: Empty JSON response");
                    }

                    JsonObject object = Railroad.GSON.fromJson(json, JsonObject.class);

                    JsonObject latestObject = object.getAsJsonObject("latest");
                    String latestStableId = latestObject.get("release").getAsString();
                    String latestSnapshotId = latestObject.get("snapshot").getAsString();

                    JsonArray versionsArray = object.getAsJsonArray("versions");
                    for (JsonElement jsonElement : versionsArray) {
                        JsonObject versionObject = jsonElement.getAsJsonObject();
                        String id = versionObject.get("id").getAsString();
                        var type = VersionType.valueOf(versionObject.get("type").getAsString().toUpperCase(Locale.ROOT));
                        String url = versionObject.get("url").getAsString();
                        String time = versionObject.get("time").getAsString().split("\\+")[0]; // Remove timezone (e.g. +00:00
                        String releaseTime = versionObject.get("releaseTime").getAsString().split("\\+")[0]; // Remove timezone (e.g. +00:00
                        versions.add(new MinecraftVersion(id, type, url, LocalDateTime.parse(time), LocalDateTime.parse(releaseTime)));
                    }

                    synchronized (MINECRAFT_VERSIONS) {
                        MINECRAFT_VERSIONS.clear();
                        MINECRAFT_VERSIONS.addAll(versions);
                    }

                    synchronized (LATEST_STABLE) {
                        LATEST_STABLE.set(MINECRAFT_VERSIONS.stream()
                                .filter(version -> version.id.equals(latestStableId))
                                .findFirst()
                                .orElse(null));
                    }

                    synchronized (LATEST_SNAPSHOT) {
                        LATEST_SNAPSHOT.set(MINECRAFT_VERSIONS.stream()
                                .filter(version -> version.id.equals(latestSnapshotId))
                                .findFirst()
                                .orElse(null));
                    }
                } catch (IOException exception) {
                    throw new RuntimeException("Failed to parse Minecraft versions", exception);
                }
            }
        });
    }

    public static ObservableList<MinecraftVersion> getSupportedVersions(ProjectType projectType) {
        switch (projectType) {
            case FORGE:
                return MINECRAFT_VERSIONS.filtered(minecraftVersion -> !ForgeVersion.getVersions(minecraftVersion).isEmpty());
            case FABRIC: // TODO: Implement Fabric support
            case QUILT: // TODO: Implement Quilt support
            case NEOFORGED: // TODO: Implement NeoForged support
        }

        return MINECRAFT_VERSIONS;
    }

    public static MinecraftVersion getLatestStableVersion() {
        return LATEST_STABLE.get();
    }

    public static MinecraftVersion getLatestSnapshotVersion() {
        return LATEST_SNAPSHOT.get();
    }

    public static Optional<MinecraftVersion> fromId(String id) {
        return MINECRAFT_VERSIONS.stream()
                .filter(version -> version.id.equals(id))
                .findFirst();
    }

    public enum VersionType {
        RELEASE,
        SNAPSHOT,
        OLD_BETA,
        OLD_ALPHA
    }
}