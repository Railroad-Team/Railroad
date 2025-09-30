package dev.railroadide.railroad.project.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.project.minecraft.pistonmeta.VersionPackage;
import dev.railroadide.railroad.utility.UrlUtils;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final String MINECRAFT_VERSIONS_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private static final List<MinecraftVersion> MINECRAFT_VERSIONS = new ArrayList<>();
    private static final ObjectProperty<MinecraftVersion> LATEST_STABLE = new SimpleObjectProperty<>();
    private static final ObjectProperty<MinecraftVersion> LATEST_SNAPSHOT = new SimpleObjectProperty<>();
    private final String id;
    private final VersionType type;
    private final String url;
    private final LocalDateTime time;
    private final LocalDateTime releaseTime;
    private final Path pistonMetaPath;

    public MinecraftVersion(String id, VersionType type, String url, LocalDateTime time, LocalDateTime releaseTime) {
        this.id = id;
        this.type = type;
        this.url = url;
        this.time = time;
        this.releaseTime = releaseTime;

        this.pistonMetaPath = ConfigHandler.getConfigDirectory().resolve("piston-meta").resolve(this.id + ".json");
    }

    public static List<MinecraftVersion> getVersionsAfter(MinecraftVersion minecraftVersion) {
        int index = MINECRAFT_VERSIONS.indexOf(minecraftVersion);
        if (index == -1 || index == MINECRAFT_VERSIONS.size() - 1)
            return List.of();

        return List.copyOf(MINECRAFT_VERSIONS.subList(index + 1, MINECRAFT_VERSIONS.size()));
    }

    public static MinecraftVersion determineBestFit(List<MinecraftVersion> versions) {
        return versions.stream()
            .filter(MinecraftVersion::isRelease)
            .max(Comparator.naturalOrder())
            .orElse(versions.isEmpty() ? null : versions.getFirst());
    }

    public static void requestMinecraftVersions() {
        Railroad.HTTP_CLIENT.newCall(new Request.Builder().url(MINECRAFT_VERSIONS_URL).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException exception) {
                Railroad.LOGGER.error("Failed to request Minecraft versions", exception);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful())
                    throw new RuntimeException("Failed to request Minecraft versions: " + response.message());

                ResponseBody body = response.body();
                String json = body.string();
                if (json.isBlank())
                    throw new RuntimeException("Failed to request Minecraft versions: Empty JSON response");

                JsonObject object = Railroad.GSON.fromJson(json, JsonObject.class);

                List<MinecraftVersion> versions = new ArrayList<>();
                JsonArray versionsArray = object.getAsJsonArray("versions");
                for (JsonElement jsonElement : versionsArray) {
                    JsonObject versionObject = jsonElement.getAsJsonObject();
                    if (!versionObject.has("id") || !versionObject.has("type") || !versionObject.has("url") || !versionObject.has("time") || !versionObject.has("releaseTime")) {
                        Railroad.LOGGER.warn("Skipping Minecraft version due to missing fields: " + versionObject);
                        continue;
                    }

                    String id = versionObject.get("id").getAsString();
                    var type = VersionType.fromString(versionObject.get("type").getAsString());
                    if (type.isEmpty()) {
                        Railroad.LOGGER.warn("Unknown Minecraft version type: " + versionObject.get("type").getAsString());
                        continue;
                    }

                    String url = versionObject.get("url").getAsString();
                    String time = versionObject.get("time").getAsString().split("\\+")[0]; // Remove timezone (e.g. +00:00)
                    String releaseTime = versionObject.get("releaseTime").getAsString().split("\\+")[0]; // Remove timezone (e.g. +00:00)
                    versions.add(new MinecraftVersion(id, type.orElseThrow(), url, LocalDateTime.parse(time), LocalDateTime.parse(releaseTime)));
                }

                MINECRAFT_VERSIONS.clear();
                MINECRAFT_VERSIONS.addAll(versions);

                JsonObject latestObject = object.getAsJsonObject("latest");
                String latestStableId = latestObject.get("release").getAsString();
                String latestSnapshotId = latestObject.get("snapshot").getAsString();

                LATEST_STABLE.set(MINECRAFT_VERSIONS.stream()
                    .filter(version -> version.id.equals(latestStableId))
                    .findFirst()
                    .orElse(null));

                LATEST_SNAPSHOT.set(MINECRAFT_VERSIONS.stream()
                    .filter(version -> version.id.equals(latestSnapshotId))
                    .findFirst()
                    .orElse(null));
            }
        });
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

    public static boolean isLatest(MinecraftVersion mcVersion) {
        return mcVersion.equals(LATEST_STABLE.get()) || mcVersion.equals(LATEST_SNAPSHOT.get());
    }

    private static Optional<MinecraftVersion> findClosestRelease(MinecraftVersion minecraftVersion) {
        if (minecraftVersion.isRelease())
            return Optional.of(minecraftVersion);

        int index = MINECRAFT_VERSIONS.indexOf(minecraftVersion);
        if (index == -1)
            return Optional.empty();

        for (int i = index - 1; i >= 0; i--) {
            MinecraftVersion version = MINECRAFT_VERSIONS.get(i);
            if (version.isRelease())
                return Optional.of(version);
        }

        for (int i = index + 1; i < MINECRAFT_VERSIONS.size(); i++) {
            MinecraftVersion version = MINECRAFT_VERSIONS.get(i);
            if (version.isRelease())
                return Optional.of(version);
        }

        return Optional.empty();
    }

    public static Optional<MinecraftVersion> getMajorVersion(MinecraftVersion minecraftVersion) {
        if (!minecraftVersion.isRelease())
            return findClosestRelease(minecraftVersion).flatMap(minecraftVersion1 -> getMajorVersion(minecraftVersion1));

        String[] split = minecraftVersion.id.split("\\.");
        if (split.length < 2)
            return Optional.empty();

        String majorVersion = split[0] + "." + split[1];
        return fromId(majorVersion);
    }

    public static List<MinecraftVersion> getVersions() {
        return List.copyOf(MINECRAFT_VERSIONS);
    }

    public CompletableFuture<VersionPackage> requestPistonMeta() {
        if (Files.exists(this.pistonMetaPath))
            return CompletableFuture.supplyAsync(() -> VersionPackage.fromFile(this.pistonMetaPath));

        CompletableFuture<VersionPackage> future = new CompletableFuture<>();
        future.completeAsync(() -> {
            try {
                UrlUtils.writeBody(this.url, this.pistonMetaPath);
                return VersionPackage.fromFile(this.pistonMetaPath);
            } catch (Exception exception) {
                throw new RuntimeException("Failed to request Piston meta for Minecraft version " + this.id, exception);
            }
        });

        return future;
    }

    public boolean isRelease() {
        return type == VersionType.RELEASE;
    }

    public String getMajorVersion() {
        if (!isRelease()) {
            Optional<MinecraftVersion> closestRelease = findClosestRelease(this);
            if (closestRelease.isEmpty())
                return id;

            return closestRelease.get().getMajorVersion();
        }

        String[] split = id.split("\\.");
        if (split.length < 2)
            return id;

        return split[0] + "." + split[1];
    }

    public String id() {
        return id;
    }

    public VersionType type() {
        return type;
    }

    public String url() {
        return url;
    }

    public LocalDateTime time() {
        return time;
    }

    public LocalDateTime releaseTime() {
        return releaseTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MinecraftVersion) obj;
        return Objects.equals(this.id, that.id) &&
            Objects.equals(this.type, that.type) &&
            Objects.equals(this.url, that.url) &&
            Objects.equals(this.time, that.time) &&
            Objects.equals(this.releaseTime, that.releaseTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, url, time, releaseTime);
    }

    @Override
    public String toString() {
        return "MinecraftVersion[" +
            "id=" + id + ", " +
            "type=" + type + ", " +
            "url=" + url + ", " +
            "time=" + time + ", " +
            "releaseTime=" + releaseTime + ']';
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        return this.releaseTime.compareTo(other.releaseTime);
    }


    public enum VersionType {
        RELEASE,
        SNAPSHOT,
        OLD_BETA,
        OLD_ALPHA;

        public static Optional<VersionType> fromString(String type) {
            return Optional.ofNullable(switch (type.toLowerCase(Locale.ROOT)) {
                case "release" -> RELEASE;
                case "snapshot" -> SNAPSHOT;
                case "old_beta" -> OLD_BETA;
                case "old_alpha" -> OLD_ALPHA;
                default -> null;
            });
        }
    }
}
