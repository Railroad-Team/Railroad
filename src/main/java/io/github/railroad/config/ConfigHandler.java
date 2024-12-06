package io.github.railroad.config;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles the file management for the config.
 * Allows for read and write operations to be executed onto the config file.
 */
public final class ConfigHandler {
    private static final ConfigHandler INSTANCE = new ConfigHandler();
    private final Config config = new Config();

    /**
     * Based on the users OS and HomePath, will return the correct path for the config directory.
     * @return {@link Path} - The path to the config directory.
     */
    public static Path getConfigDirectory() {
        String os = System.getProperty("os.name"); // TODO: Create Enum for OS
        String homePath = System.getProperty("user.home");
        if (os.startsWith("Linux")) {
            return Paths.get(homePath, ".config", "Railroad");
        } else if (os.startsWith("Windows")) {
            return Paths.get(homePath, "AppData", "Roaming", "Railroad");
        } else if (os.startsWith("Mac") || os.startsWith("Darwin")) {
            return Paths.get(homePath, "Library", "Application Support", "Railroad");
        } else {
            return Paths.get(homePath, "Railroad");
        }
    }

    /**
     * Updates the config file with the new config.
     * @param newConfig {@link Config} - The config for the file to be updated with.
     */
    public static void updateConfig(@Nullable Config newConfig) {
        //TODO simplify this to saveConfig() and INSTANCE.config.copyFrom(newConfig) ????
        Railroad.LOGGER.info("{} config file", newConfig == null ? "Initializing" : "Updating");

        Path railroadDataPath = getConfigDirectory();
        try {
            Files.createDirectories(railroadDataPath);
            INSTANCE.config.copyFrom(newConfig);
            Files.writeString(railroadDataPath.resolve("config.json"), Railroad.GSON.toJson(INSTANCE.config.toJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("Error updating config.json", exception);
        }
    }

    public static void saveConfig() {
        Railroad.LOGGER.info("Updating config file");

        Path railroadDataPath = getConfigDirectory();
        try {
            Files.createDirectories(railroadDataPath);
            Files.writeString(railroadDataPath.resolve("config.json"), Railroad.GSON.toJson(INSTANCE.config.toJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("Error updating config.json", exception);
        }
    }

    /**
     * If the config file does not exist, it will create the file and write the default config to it.
     * If the config file does exist, it will read the file and parse the JSON into the config object.
     */
    public static void initConfig() {
        Railroad.LOGGER.info("Initializing config file");

        Path railroadDataPath = getConfigDirectory();
        try {
            Files.createDirectories(railroadDataPath);
            if (Files.notExists(railroadDataPath.resolve("config.json"))) {
                Files.writeString(railroadDataPath.resolve("config.json"), Railroad.GSON.toJson(INSTANCE.config.toJson()));
            } else {
                String configJson = Files.readString(railroadDataPath.resolve("config.json"));
                INSTANCE.config.fromJson(Railroad.GSON.fromJson(configJson, JsonObject.class));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Error initializing config.json", exception);
        }
    }

    public static Config getConfig() {
        return INSTANCE.config;
    }
}
