package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

public record JavaVersion(String component, int majorVersion) {
    public static JavaVersion fromJson(JsonObject json) {
        return Railroad.GSON.fromJson(json, JavaVersion.class);
    }
}
