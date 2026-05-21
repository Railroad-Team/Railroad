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
    private final Map<String, Boolean> inspectionRuleEnabledOverrides = new LinkedHashMap<>();
    private final Map<String, Boolean> inspectionRuleTagEnabledOverrides = new LinkedHashMap<>();
    private final Map<String, String> inspectionRuleSeverityOverrides = new LinkedHashMap<>();

    public Map<String, Boolean> getEnabledPlugins() {
        return new LinkedHashMap<>(enabledPlugins);
    }

    public void setEnabledPlugins(Map<String, Boolean> enabledPlugins) {
        this.enabledPlugins.clear();
        if (enabledPlugins == null || enabledPlugins.isEmpty())
            return;

        this.enabledPlugins.putAll(enabledPlugins);
    }

    public Map<String, Boolean> getInspectionRuleEnabledOverrides() {
        return new LinkedHashMap<>(inspectionRuleEnabledOverrides);
    }

    public void setInspectionRuleEnabledOverrides(Map<String, Boolean> overrides) {
        inspectionRuleEnabledOverrides.clear();
        if (overrides == null || overrides.isEmpty())
            return;

        inspectionRuleEnabledOverrides.putAll(overrides);
    }

    public Map<String, Boolean> getInspectionRuleTagEnabledOverrides() {
        return new LinkedHashMap<>(inspectionRuleTagEnabledOverrides);
    }

    public void setInspectionRuleTagEnabledOverrides(Map<String, Boolean> overrides) {
        inspectionRuleTagEnabledOverrides.clear();
        if (overrides == null || overrides.isEmpty())
            return;

        inspectionRuleTagEnabledOverrides.putAll(overrides);
    }

    public Map<String, String> getInspectionRuleSeverityOverrides() {
        return new LinkedHashMap<>(inspectionRuleSeverityOverrides);
    }

    public void setInspectionRuleSeverityOverrides(Map<String, String> overrides) {
        inspectionRuleSeverityOverrides.clear();
        if (overrides == null || overrides.isEmpty())
            return;

        inspectionRuleSeverityOverrides.putAll(overrides);
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

        JsonObject inspectionRuleEnabledJson = new JsonObject();
        for (Map.Entry<String, Boolean> entry : inspectionRuleEnabledOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                continue;
            inspectionRuleEnabledJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("InspectionRuleEnabledOverrides", inspectionRuleEnabledJson);

        JsonObject inspectionRuleTagEnabledJson = new JsonObject();
        for (Map.Entry<String, Boolean> entry : inspectionRuleTagEnabledOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                continue;
            inspectionRuleTagEnabledJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("InspectionRuleTagEnabledOverrides", inspectionRuleTagEnabledJson);

        JsonObject inspectionRuleSeverityJson = new JsonObject();
        for (Map.Entry<String, String> entry : inspectionRuleSeverityOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank())
                continue;
            inspectionRuleSeverityJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("InspectionRuleSeverityOverrides", inspectionRuleSeverityJson);

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        enabledPlugins.clear();
        inspectionRuleEnabledOverrides.clear();
        inspectionRuleTagEnabledOverrides.clear();
        inspectionRuleSeverityOverrides.clear();

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

        if (json.has("InspectionRuleEnabledOverrides")) {
            JsonElement enabledRulesElement = json.get("InspectionRuleEnabledOverrides");
            if (enabledRulesElement.isJsonObject()) {
                JsonObject enabledRulesJson = enabledRulesElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : enabledRulesJson.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !entry.getValue().isJsonPrimitive())
                        continue;
                    inspectionRuleEnabledOverrides.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        }

        if (json.has("InspectionRuleTagEnabledOverrides")) {
            JsonElement enabledTagsElement = json.get("InspectionRuleTagEnabledOverrides");
            if (enabledTagsElement.isJsonObject()) {
                JsonObject enabledTagsJson = enabledTagsElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : enabledTagsJson.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !entry.getValue().isJsonPrimitive())
                        continue;
                    inspectionRuleTagEnabledOverrides.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        }

        if (json.has("InspectionRuleSeverityOverrides")) {
            JsonElement severityElement = json.get("InspectionRuleSeverityOverrides");
            if (severityElement.isJsonObject()) {
                JsonObject severityJson = severityElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : severityJson.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !entry.getValue().isJsonPrimitive())
                        continue;
                    inspectionRuleSeverityOverrides.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
    }
}
