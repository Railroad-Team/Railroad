package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.railroadide.core.utility.ServiceLocator;

public record AssetIndex(String id, String sha1, int size, int totalSize, String url) {
    public static AssetIndex fromJson(JsonObject json) {
        return ServiceLocator.getService(Gson.class).fromJson(json, AssetIndex.class);
    }
}
