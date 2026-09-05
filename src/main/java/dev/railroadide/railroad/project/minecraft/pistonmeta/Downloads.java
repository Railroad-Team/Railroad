package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Downloads available for a Minecraft version.
 *
 * @param client the client executable download
 * @param clientMappings the client mappings download, or null when absent
 * @param server the server executable download
 * @param serverMappings the server mappings download, or null when absent
 */
public record Downloads(
    Download client,
    @SerializedName("client_mappings") Download clientMappings,
    Download server,
    @SerializedName("server_mappings") Download serverMappings
) {
    /**
     * Parses the client, server, and mapping downloads from JSON.
     *
     * @param json the metadata object
     * @return the parsed downloads
     */
    public static Downloads fromJson(JsonObject json) {
        JsonObject clientJson = json.getAsJsonObject("client");
        Download client = Download.fromJson(clientJson);

        JsonObject clientMappingsJson = json.getAsJsonObject("client_mappings");
        Download clientMappings = Download.fromJson(clientMappingsJson);

        JsonObject serverJson = json.getAsJsonObject("server");
        Download server = Download.fromJson(serverJson);

        JsonObject serverMappingsJson = json.getAsJsonObject("server_mappings");
        Download serverMappings = Download.fromJson(serverMappingsJson);

        return new Downloads(client, clientMappings, server, serverMappings);
    }
}
