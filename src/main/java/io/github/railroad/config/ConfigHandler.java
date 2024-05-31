package io.github.railroad.config;

import io.github.railroad.Railroad;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigHandler {
    private static final ConfigHandler INSTANCE = new ConfigHandler();
    private final AtomicReference<Config> config = new AtomicReference<>(new Config());

    private ConfigHandler() {
    }

    public static Path getAppDataPath() {
        // TODO: Implement MacOS support
        var os = System.getProperty("os.name");
        var homePath = System.getProperty("user.home");
        if (os.startsWith("Linux")) {
            return Paths.get(homePath, ".config", "Railroad");
        } else if (os.startsWith("Windows")) {
            return Paths.get(homePath, "AppData", "Roaming", "Railroad");
        } else {
            return Paths.get("");
        }
    }

    public static void updateConfig(@Nullable Config newConfig) {
        Railroad.LOGGER.info("{} config file", newConfig == null ? "Initializing" : "Updating");

        Path railroadDataPath = getAppDataPath();
        try {
            Files.createDirectories(railroadDataPath);
            INSTANCE.config.set(newConfig == null ? INSTANCE.config.get() : newConfig);
            Files.writeString(railroadDataPath.resolve("config.json"), Railroad.GSON.toJson(INSTANCE.config.get().toJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("Error updating config.json", exception);
        }
    }

    public static void initConfig() {
        updateConfig(null);
    }
}
