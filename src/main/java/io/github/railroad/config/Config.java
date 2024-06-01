package io.github.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.project.data.Project;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class Config implements JsonSerializable<JsonObject> {
    private final ObjectProperty<Settings> settings = new ReadOnlyObjectWrapper<>(new Settings());

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
        json.add("Settings", this.settings.get().toJson());

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if(json.has("Projects")) {
            JsonElement projects = json.get("Projects");
            if(projects.isJsonArray()) {
                JsonArray projectsArray = projects.getAsJsonArray();
                for(JsonElement project : projectsArray) {
                    if(!project.isJsonObject())
                        continue;

                    Project.createFromJson(project.getAsJsonObject());
                }
            }
        }

        if(json.has("Plugins")) {
            JsonElement plugins = json.get("Plugins");
            if(plugins.isJsonArray()) {
                Railroad.PLUGIN_MANAGER.unloadPlugins();
                Railroad.PLUGIN_MANAGER.getPluginList().clear();

                JsonArray pluginsArray = plugins.getAsJsonArray();
                for(JsonElement plugin : pluginsArray) {
                    if(!plugin.isJsonPrimitive())
                        continue;

                    JsonPrimitive pluginPrimitive = plugin.getAsJsonPrimitive();
                    if(!pluginPrimitive.isString())
                        continue;

                    Railroad.PLUGIN_MANAGER.addPlugin(pluginPrimitive.getAsString());
                }
            }
        }

        if(json.has("Settings")) {
            JsonElement settings = json.get("Settings");
            if(settings.isJsonObject()) {
                this.settings.get().fromJson(settings.getAsJsonObject());
            }
        }
    }

    public void copyFrom(@Nullable Config config) {
        if(config == null)
            return;

        this.settings.get().copyFrom(config.getSettings());
    }

    public Settings getSettings() {
        return settings.get();
    }
}
