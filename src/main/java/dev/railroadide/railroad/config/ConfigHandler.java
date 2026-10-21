package dev.railroadide.railroad.config;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.Railroad;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Provides access to Railroad's process-wide configuration and persists it to
 * the platform-specific configuration directory.
 */
public final class ConfigHandler {
    private static final ConfigHandler INSTANCE = new ConfigHandler();
    private final Config config = new Config();

    /**
     * Resolves the directory used to store Railroad's configuration.
     *
     * <p>
     * On Windows, {@code APPDATA} is preferred; on Linux,
     * {@code XDG_CONFIG_HOME} is preferred. If the relevant environment
     * variable is missing or blank, the platform's conventional directory is
     * used instead.
     * </p>
     *
     * @return the platform-specific Railroad configuration directory
     */
    public static Path getConfigDirectory() {
        String userHome = System.getProperty("user.home");
        return switch (OperatingSystem.CURRENT) {
            case WINDOWS -> {
                String roaming = System.getenv("APPDATA");
                if (roaming != null && !roaming.isBlank())
                    yield Path.of(roaming, "Railroad");

                yield Path.of(userHome, "AppData", "Roaming", "Railroad");
            }
            case MAC -> Path.of(userHome, "Library", "Application Support", "Railroad");
            case LINUX -> {
                String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
                if (xdgConfigHome != null && !xdgConfigHome.isBlank())
                    yield Path.of(xdgConfigHome, "Railroad");

                yield Path.of(userHome, ".config", "Railroad");
            }
            case UNKNOWN -> {
                Railroad.LOGGER.warn("Unknown operating system, using default config directory");
                yield Path.of(userHome, "Railroad");
            }
        };
    }

    /**
     * Saves the current configuration to {@code config.json}.
     *
     * <p>
     * The configuration is written to a temporary file and then moved into
     * place, using an atomic move when the file system supports it. The
     * configuration directory is created if necessary.
     * </p>
     *
     * @throws IllegalStateException if the configuration cannot be written
     */
    public static synchronized void saveConfig() {
        Railroad.LOGGER.info("Updating config file");

        Path railroadDataPath = getConfigDirectory();
        try {
            Files.createDirectories(railroadDataPath);
            Path configPath = railroadDataPath.resolve("config.json");
            Path temporaryConfigPath = Files.createTempFile(railroadDataPath, "config-", ".json.tmp");
            try {
                Files.writeString(temporaryConfigPath, Railroad.GSON.toJson(INSTANCE.config.toJson()));
                try {
                    Files.move(
                        temporaryConfigPath,
                        configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException _) {
                    Files.move(temporaryConfigPath, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporaryConfigPath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Error updating config.json", exception);
        }
    }

    /**
     * Initializes the configuration from {@code config.json}.
     *
     * <p>
     * The configuration directory is created if necessary. If no
     * configuration file exists, a new file containing the current default
     * configuration is created; otherwise, the existing file is read and
     * deserialized.
     * </p>
     *
     * @throws IllegalStateException if the configuration directory or file
     *             cannot be accessed
     */
    public static void initConfig() {
        Railroad.LOGGER.info("Initializing config file");

        Path railroadDataPath = getConfigDirectory();
        try {
            Files.createDirectories(railroadDataPath);
            if (Files.notExists(railroadDataPath.resolve("config.json"))) {
                Files.writeString(railroadDataPath.resolve("config.json"),
                    Railroad.GSON.toJson(INSTANCE.config.toJson()));
            } else {
                String configJson = Files.readString(railroadDataPath.resolve("config.json"));
                INSTANCE.config.fromJson(Railroad.GSON.fromJson(configJson, JsonObject.class));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Error initializing config.json", exception);
        }
    }

    /**
     * Returns the shared in-memory configuration.
     *
     * @return the configuration managed by this handler
     */
    public static Config getConfig() {
        return INSTANCE.config;
    }
}
