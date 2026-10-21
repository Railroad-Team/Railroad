package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

/**
 * Java runtime requirements published for a Minecraft version.
 *
 * @param component the launcher runtime component identifier
 * @param majorVersion the required Java major version
 */
public record JavaVersion(String component, int majorVersion) {
    /**
     * Parses Java runtime requirements from JSON.
     *
     * @param json the metadata object
     * @return the parsed runtime requirements, or null if the JSON object is null
     */
    public static JavaVersion fromJson(JsonObject json) {
        return Railroad.GSON.fromJson(json, JavaVersion.class);
    }
}
