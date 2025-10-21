package dev.railroadide.railroad.config;

import com.google.gson.JsonObject;
import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.Railroad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigHandler {
    private static final ConfigHandler INSTANCE = new ConfigHandler();
    private final Config config = new Config();

    public static Path getConfigDirectory() {
        OperatingSystem os = OperatingSystem.CURRENT;
        String userHome = System.getProperty("user.home");
        return switch (os) {
            case WINDOWS -> {
                String roaming = System.getenv("APPDATA");
                if (roaming != null && !roaming.isBlank()) {
                    yield Path.of(roaming, "Railroad");
                }

                yield Path.of(userHome, "AppData", "Roaming", "Railroad");
            }
            case MAC -> Path.of(userHome, "Library", "Application Support", "Railroad");
            case LINUX -> {
                String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
                if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
                    yield Path.of(xdgConfigHome, "Railroad");
                }

                yield Path.of(userHome, ".config", "Railroad");
            }
            case UNKNOWN -> {
                Railroad.LOGGER.warn("Unknown operating system, using default config directory");
                yield Path.of(userHome, "Railroad");
            }
        };
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
