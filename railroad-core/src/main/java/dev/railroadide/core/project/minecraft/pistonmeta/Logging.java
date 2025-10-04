package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.core.gson.GsonLocator;

public record Logging(Client client) {
    public static Logging fromJson(JsonObject json) {
        JsonObject clientJson = json.getAsJsonObject("client");
        Client client = Client.fromJson(clientJson);

        return new Logging(client);
    }

    public record Client(String argument, LoggingFile file, String type) {
        public static Client fromJson(JsonObject json) {
            String argument = json.get("argument").getAsString();

            JsonObject fileJson = json.getAsJsonObject("file");
            LoggingFile file = LoggingFile.fromJson(fileJson);

            String type = json.get("type").getAsString();

            return new Client(argument, file, type);
        }

        public record LoggingFile(String id, String sha1, int size, String url) {
            public static LoggingFile fromJson(JsonObject json) {
                return GsonLocator.getInstance().fromJson(json, LoggingFile.class);
            }
        }
    }
}
