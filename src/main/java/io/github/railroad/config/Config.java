package io.github.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.project.Project;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class Config implements JsonSerializable<JsonObject> {
    @Override
    public JsonObject toJson() {
        var json = new JsonObject();

        var projects = new JsonArray();
        for (Project project : Railroad.PROJECT_MANAGER.getProjects()) {
            projects.add(project.toJson());
        }

        json.add("Projects", projects);

        var plugins = new JsonArray();
        for (Plugin plugin : Railroad.PLUGIN_MANAGER.getPluginList()) {
            plugins.add(plugin.toJson());
        }

        json.add("Plugins", plugins);

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if (json.has("Projects")) {
            JsonElement projects = json.get("Projects");
            if (projects.isJsonArray()) {
                JsonArray projectsArray = projects.getAsJsonArray();
                for (JsonElement project : projectsArray) {
                    if (!project.isJsonObject())
                        continue;

                    Optional<Project> optProject = Project.createFromJson(project.getAsJsonObject());
                    optProject.ifPresent(Railroad.PROJECT_MANAGER::newProject);
                }
            }
        }

        if (json.has("Plugins")) {
            JsonElement plugins = json.get("Plugins");
            if (plugins.isJsonArray()) {
                Railroad.PLUGIN_MANAGER.unloadPlugins();
                Railroad.PLUGIN_MANAGER.getPluginList().clear();

                JsonArray pluginsArray = plugins.getAsJsonArray();
                for (JsonElement plugin : pluginsArray) {
                    if (!plugin.isJsonPrimitive() || !plugin.getAsJsonPrimitive().isString())
                        continue;

                    Railroad.PLUGIN_MANAGER.addPlugin(plugin.getAsString());
                }
            }
        }
    }
}
