package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public record Downloads(Download client, @SerializedName("client_mappings") Download clientMappings, Download server, @SerializedName("server_mappings") Download serverMappings) {
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
