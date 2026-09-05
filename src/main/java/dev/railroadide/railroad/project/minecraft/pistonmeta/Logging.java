package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

/**
 * Logging configuration supplied by Minecraft version metadata.
 *
 * @param client the client logging configuration
 */
public record Logging(Client client) {
    /**
     * Parses the client logging configuration from version metadata.
     *
     * @param json the logging metadata object
     * @return the parsed logging configuration
     */
    public static Logging fromJson(JsonObject json) {
        JsonObject clientJson = json.getAsJsonObject("client");
        Client client = Client.fromJson(clientJson);

        return new Logging(client);
    }

    /**
     * Client logging argument and configuration file metadata.
     *
     * @param argument the JVM logging argument template
     * @param file the logging configuration file download
     * @param type the logging configuration format
     */
    public record Client(String argument, LoggingFile file, String type) {
        /**
         * Parses client logging settings.
         *
         * @param json the client logging metadata object
         * @return the parsed client configuration
         */
        public static Client fromJson(JsonObject json) {
            String argument = json.get("argument").getAsString();

            JsonObject fileJson = json.getAsJsonObject("file");
            LoggingFile file = LoggingFile.fromJson(fileJson);

            String type = json.get("type").getAsString();

            return new Client(argument, file, type);
        }

        /**
         * Metadata for a downloadable client logging configuration file.
         *
         * @param id the configuration file identifier
         * @param sha1 the expected SHA-1 hash
         * @param size the expected file size in bytes
         * @param url the configuration file download URL
         */
        public record LoggingFile(String id, String sha1, int size, String url) {
            /**
             * Parses logging configuration file metadata.
             *
             * @param json the file metadata object
             * @return the parsed file metadata, or null if the JSON object is null
             */
            public static LoggingFile fromJson(JsonObject json) {
                return Railroad.GSON.fromJson(json, LoggingFile.class);
            }
        }
    }
}
