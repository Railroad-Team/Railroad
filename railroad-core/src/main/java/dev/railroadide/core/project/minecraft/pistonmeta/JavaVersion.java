package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.railroadide.core.utility.ServiceLocator;

public record JavaVersion(String component, int majorVersion) {
    public static JavaVersion fromJson(JsonObject json) {
        return ServiceLocator.getService(Gson.class).fromJson(json, JavaVersion.class);
    }
}
