package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

/**
 * Metadata describing a Minecraft asset index download.
 *
 * @param id the asset index identifier
 * @param sha1 the expected SHA-1 hash of the index
 * @param size the index size in bytes
 * @param totalSize the total size of the indexed assets in bytes
 * @param url the asset index download URL
 */
public record AssetIndex(String id, String sha1, int size, int totalSize, String url) {
    /**
     * Parses asset index metadata from JSON.
     *
     * @param json the metadata object
     * @return the parsed asset index, or null if the JSON object is null
     */
    public static AssetIndex fromJson(JsonObject json) {
        return Railroad.GSON.fromJson(json, AssetIndex.class);
    }
}
