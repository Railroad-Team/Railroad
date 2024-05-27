package io.github.railroad.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.github.railroad.Railroad.LOGGER;

public final class ConfigHandler {
    private static JsonObject setting_obj;

    private ConfigHandler() {
    }

    public static void checkAndCreateDefaultJsonObjects(JsonObject object) {
        if (!object.has("projects")) {
            var projectsArray = new JsonArray();
            object.add("projects", projectsArray);
        }
        ;
        if (!object.has("settings")) {
            JsonObject railroadsettings = new JsonObject();
            object.add("settings", railroadsettings);
        }
        if (object.has("settings")) {
            JsonObject railroadsettings = object.getAsJsonObject("settings");
            if (!railroadsettings.has("plugins")) {
                JsonArray railroadplugins = new JsonArray();
                railroadsettings.add("plugins", railroadplugins);
            }
            if (!railroadsettings.has("plugin_settings")) {
                JsonArray plugin_settings = new JsonArray();
                railroadsettings.add("plugin_settings", plugin_settings);
            }
            if (!railroadsettings.has("theme")) {
                railroadsettings.addProperty("theme", "default-dark");
            }
            if (!railroadsettings.has("language")) {
                railroadsettings.addProperty("language", "en_us");
            }
        }
    }

    public static JsonObject getPluginSettings(String plugin_name, boolean create_if_not_exists) {
        JsonObject object = getConfigJson();
        JsonArray plugin_settings = object.get("settings").getAsJsonObject().get("plugin_settings").getAsJsonArray();
        for (JsonElement setting : plugin_settings) {
            if (setting.isJsonObject()) {
                if (setting.getAsJsonObject().get("name").getAsString().equals(plugin_name)) {
                    return setting.getAsJsonObject();
                }
            }
        }
        
        var new_obj = new JsonObject();
        new_obj.addProperty("name", plugin_name);
        plugin_settings.add(new_obj);
        return new_obj;
    }

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
                checkAndCreateDefaultJsonObjects(initialData);
                Files.writeString(projectsJsonPath, Railroad.GSON.toJson(initialData));
            } catch (IOException exception) {
                throw new IllegalStateException("Error creating config.json", exception);
            }
        }
    }

    public static JsonObject getConfigJson() {
        if (setting_obj != null) {
            return setting_obj;
        } else {
            try {
                LOGGER.debug("Reading config file");
                Path projectsJsonPath = getConfigPath().resolve("config.json");
                if (Files.notExists(projectsJsonPath)) {
                    createProjectsJsonIfNotExists();
                }

                JsonObject obj = Railroad.GSON.fromJson(Files.readString(projectsJsonPath), JsonObject.class);
                checkAndCreateDefaultJsonObjects(obj);
                setting_obj = obj;
                return setting_obj;
            } catch (IOException exception) {
                throw new IllegalStateException("Error reading config.json", exception);
            }
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

    public static void updateConfig() {
        LOGGER.info("Updating config file");

        Path projectsJsonPath = getConfigPath().resolve("config.json");
        if (Files.notExists(projectsJsonPath)) {
            createProjectsJsonIfNotExists();
        }

        try {
            Files.writeString(projectsJsonPath, Railroad.GSON.toJson(getConfigJson()));
            LOGGER.info("UPDATING CONFIG {}", Railroad.GSON.toJson(getConfigJson()));
        } catch (IOException exception) {
            throw new IllegalStateException("Error updating config.json", exception);
        }
    }
}
