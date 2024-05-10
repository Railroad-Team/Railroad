package io.github.railroad.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigHandler {
    private ConfigHandler() {}

    public static void createDefaultConfigs() {
        try {
            Files.createDirectories(getConfigPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Error creating config directory", exception);
        }

        createProjectsJsonIfNotExists();
    }

    private static void createProjectsJsonIfNotExists() {
        Path projectsJsonPath = getConfigPath().resolve("config.json");
        if (Files.notExists(projectsJsonPath)) {
            try {
                var initialData = new JsonObject();
                var projectsArray = new JsonArray();
                initialData.add("projects", projectsArray);
                Files.writeString(projectsJsonPath, Railroad.GSON.toJson(initialData));
            } catch (IOException exception) {
                throw new IllegalStateException("Error creating config.json", exception);
            }
        }
    }

    public static JsonObject getConfigJson() {
        try {
            Path projectsJsonPath = getConfigPath().resolve("config.json");
            if (Files.notExists(projectsJsonPath)) {
                createProjectsJsonIfNotExists();
            }

            return Railroad.GSON.fromJson(Files.readString(projectsJsonPath), JsonObject.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Error reading config.json", exception);
        }
    }

    public static JsonArray getProjectsConfig() {
        return getConfigJson().getAsJsonArray("projects");
    }

    public static Path getConfigPath() {
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

    public static void updateConfig(JsonObject obj) {
        System.out.println("Updating config file");

        Path projectsJsonPath = getConfigPath().resolve("config.json");
        if (Files.notExists(projectsJsonPath)) {
            createProjectsJsonIfNotExists();
        }

        try {
            Files.writeString(projectsJsonPath, Railroad.GSON.toJson(obj));
        } catch (IOException exception) {
            throw new IllegalStateException("Error updating config.json", exception);
        }
    }
}
