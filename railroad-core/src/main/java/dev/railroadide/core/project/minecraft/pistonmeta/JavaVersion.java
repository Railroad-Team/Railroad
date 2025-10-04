package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.core.gson.GsonLocator;

public record JavaVersion(String component, int majorVersion) {
    public static JavaVersion fromJson(JsonObject json) {
        return GsonLocator.getInstance().fromJson(json, JavaVersion.class);
    }
}
