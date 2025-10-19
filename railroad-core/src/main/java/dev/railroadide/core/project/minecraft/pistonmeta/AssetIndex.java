package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.core.gson.GsonLocator;

public record AssetIndex(String id, String sha1, int size, int totalSize, String url) {
    public static AssetIndex fromJson(JsonObject json) {
        return GsonLocator.getInstance().fromJson(json, AssetIndex.class);
    }
}
