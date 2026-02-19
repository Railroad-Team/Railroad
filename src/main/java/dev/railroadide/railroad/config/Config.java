package dev.railroadide.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.utility.json.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Config implements JsonSerializable<JsonObject> {
    private final Map<String, Boolean> enabledPlugins = new LinkedHashMap<>();

    public Map<String, Boolean> getEnabledPlugins() {
        return new LinkedHashMap<>(enabledPlugins);
    }

    public void setEnabledPlugins(Map<String, Boolean> enabledPlugins) {
        this.enabledPlugins.clear();
        if (enabledPlugins == null || enabledPlugins.isEmpty())
            return;

        this.enabledPlugins.putAll(enabledPlugins);
    }

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();

        var projects = new JsonArray();
        for (Project project : Railroad.PROJECT_MANAGER.getProjects()) {
            projects.add(project.toJson());
        }

        json.add("Projects", projects);

        var enabledPluginsJson = new JsonObject();
        for (Map.Entry<String, Boolean> entry : enabledPlugins.entrySet()) {
            String pluginId = entry.getKey();
            Boolean enabled = entry.getValue();
            if (pluginId == null || pluginId.isBlank() || enabled == null)
                continue;

            enabledPluginsJson.addProperty(pluginId, enabled);
        }

        json.add("EnabledPlugins", enabledPluginsJson);

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        enabledPlugins.clear();

        if (json.has("Projects")) {
            JsonElement projects = json.get("Projects");
            if (projects.isJsonArray()) {
                JsonArray projectsArray = projects.getAsJsonArray();
                for (JsonElement project : projectsArray) {
                    if (!project.isJsonObject())
                        continue;

                    Optional<RailroadProject> optProject = RailroadProject.createFromJson(project.getAsJsonObject());
                    optProject.ifPresent(Railroad.PROJECT_MANAGER::newProject);
                }
            }
        }

        if (json.has("EnabledPlugins")) {
            JsonElement enabledPluginsElement = json.get("EnabledPlugins");
            if (enabledPluginsElement.isJsonObject()) {
                JsonObject enabledPluginsJson = enabledPluginsElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : enabledPluginsJson.entrySet()) {
                    String pluginId = entry.getKey();
                    JsonElement enabledJson = entry.getValue();
                    if (pluginId == null || pluginId.isBlank() || enabledJson == null || !enabledJson.isJsonPrimitive())
                        continue;

                    enabledPlugins.put(pluginId, enabledJson.getAsBoolean());
                }
            }
        }
    }
}
