package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

public record AssetIndex(String id, String sha1, int size, int totalSize, String url) {
    public static AssetIndex fromJson(JsonObject json) {
        return Railroad.GSON.fromJson(json, AssetIndex.class);
    }
}
