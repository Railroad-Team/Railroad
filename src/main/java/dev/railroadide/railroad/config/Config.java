package dev.railroadide.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.utility.json.JsonSerializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores Railroad's application configuration, including project entries,
 * plugin enablement, and inspection rule overrides.
 *
 * <p>
 * Configuration maps are kept in insertion order and are serialized to
 * JSON using the property names expected by Railroad's configuration format.
 * The map getters return copies so callers cannot modify the configuration
 * without using a setter.
 * </p>
 */
public class Config implements JsonSerializable<JsonObject> {
    private final Map<String, Boolean> enabledPlugins = new LinkedHashMap<>();
    private final Map<String, Boolean> inspectionRuleEnabledOverrides = new LinkedHashMap<>();
    private final Map<String, Boolean> inspectionRuleTagEnabledOverrides = new LinkedHashMap<>();
    private final Map<String, String> inspectionRuleSeverityOverrides = new LinkedHashMap<>();

    /**
     * Returns the configured plugin enablement states.
     *
     * @return a copy mapping plugin IDs to their enabled states
     */
    public Map<String, Boolean> getEnabledPlugins() {
        return new LinkedHashMap<>(enabledPlugins);
    }

    /**
     * Replaces the configured plugin enablement states.
     *
     * <p>
     * A {@code null} or empty map clears the current configuration. The
     * supplied map is copied, so later changes to it do not affect this
     * configuration.
     * </p>
     *
     * @param enabledPlugins a map from plugin IDs to enabled states, or
     *            {@code null} to clear the configuration
     */
    public void setEnabledPlugins(Map<String, Boolean> enabledPlugins) {
        this.enabledPlugins.clear();
        if (enabledPlugins == null || enabledPlugins.isEmpty())
            return;

        this.enabledPlugins.putAll(enabledPlugins);
    }

    /**
     * Returns inspection rule enablement overrides.
     *
     * @return a copy mapping inspection rule IDs to their enabled states
     */
    public Map<String, Boolean> getInspectionRuleEnabledOverrides() {
        return new LinkedHashMap<>(inspectionRuleEnabledOverrides);
    }

    /**
     * Replaces inspection rule enablement overrides.
     *
     * <p>
     * A {@code null} or empty map clears the current overrides. The
     * supplied map is copied, so later changes to it do not affect this
     * configuration.
     * </p>
     *
     * @param overrides a map from inspection rule IDs to enabled states, or
     *            {@code null} to clear the overrides
     */
    public void setInspectionRuleEnabledOverrides(Map<String, Boolean> overrides) {
        inspectionRuleEnabledOverrides.clear();
        if (overrides == null || overrides.isEmpty())
            return;

        inspectionRuleEnabledOverrides.putAll(overrides);
    }

    /**
     * Returns inspection rule tag enablement overrides.
     *
     * @return a copy mapping inspection rule tags to their enabled states
     */
    public Map<String, Boolean> getInspectionRuleTagEnabledOverrides() {
        return new LinkedHashMap<>(inspectionRuleTagEnabledOverrides);
    }

    /**
     * Replaces inspection rule tag enablement overrides.
     *
     * <p>
     * A {@code null} or empty map clears the current overrides. The
     * supplied map is copied, so later changes to it do not affect this
     * configuration.
     * </p>
     *
     * @param overrides a map from inspection rule tags to enabled states, or
     *            {@code null} to clear the overrides
     */
    public void setInspectionRuleTagEnabledOverrides(Map<String, Boolean> overrides) {
        inspectionRuleTagEnabledOverrides.clear();
        if (overrides == null || overrides.isEmpty())
            return;

        inspectionRuleTagEnabledOverrides.putAll(overrides);
    }

    /**
     * Returns inspection rule severity overrides.
     *
     * @return a copy mapping inspection rule IDs to severity names
     */
    public Map<String, String> getInspectionRuleSeverityOverrides() {
        return new LinkedHashMap<>(inspectionRuleSeverityOverrides);
    }

    /**
     * Replaces inspection rule severity overrides.
     *
     * <p>
     * A {@code null} or empty map clears the current overrides. The
     * supplied map is copied, so later changes to it do not affect this
     * configuration.
     * </p>
     *
     * @param overrides a map from inspection rule IDs to severity names, or
     *            {@code null} to clear the overrides
     */
    public void setInspectionRuleSeverityOverrides(Map<String, String> overrides) {
        inspectionRuleSeverityOverrides.clear();
        if (overrides == null || overrides.isEmpty())
            return;

        inspectionRuleSeverityOverrides.putAll(overrides);
    }

    /**
     * Serializes this configuration to JSON.
     *
     * <p>
     * The current projects are read from Railroad's project manager. Map
     * entries with blank keys or unsupported {@code null} values are omitted
     * from the resulting JSON.
     * </p>
     *
     * @return the JSON representation of this configuration
     */
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

        var inspectionRuleEnabledJson = new JsonObject();
        for (Map.Entry<String, Boolean> entry : inspectionRuleEnabledOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                continue;
            inspectionRuleEnabledJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("InspectionRuleEnabledOverrides", inspectionRuleEnabledJson);

        var inspectionRuleTagEnabledJson = new JsonObject();
        for (Map.Entry<String, Boolean> entry : inspectionRuleTagEnabledOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                continue;
            inspectionRuleTagEnabledJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("InspectionRuleTagEnabledOverrides", inspectionRuleTagEnabledJson);

        var inspectionRuleSeverityJson = new JsonObject();
        for (Map.Entry<String, String> entry : inspectionRuleSeverityOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                || entry.getValue().isBlank())
                continue;
            inspectionRuleSeverityJson.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("InspectionRuleSeverityOverrides", inspectionRuleSeverityJson);

        return json;
    }

    /**
     * Loads this configuration from JSON.
     *
     * <p>
     * Existing projects and overrides are cleared before loading. Valid
     * project entries replace the projects in Railroad's project manager;
     * malformed entries and entries with invalid value types are ignored.
     * </p>
     *
     * @param json the JSON object containing the configuration to load
     */
    @Override
    public void fromJson(JsonObject json) {
        enabledPlugins.clear();
        inspectionRuleEnabledOverrides.clear();
        inspectionRuleTagEnabledOverrides.clear();
        inspectionRuleSeverityOverrides.clear();

        List<Project> loadedProjects = new ArrayList<>();
        if (json.has("Projects")) {
            JsonElement projects = json.get("Projects");
            if (projects.isJsonArray()) {
                JsonArray projectsArray = projects.getAsJsonArray();
                for (JsonElement project : projectsArray) {
                    if (!project.isJsonObject())
                        continue;

                    Optional<RailroadProject> optProject = RailroadProject.createFromJson(project.getAsJsonObject());
                    optProject.ifPresent(loadedProjects::add);
                }
            }
        }
        Railroad.PROJECT_MANAGER.setProjects(loadedProjects);
        if (loadedProjects.size() != Railroad.PROJECT_MANAGER.getProjects().size()) {
            Railroad.LOGGER.warn(
                "Removed {} duplicate project entries while loading configuration",
                loadedProjects.size() - Railroad.PROJECT_MANAGER.getProjects().size());
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
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                        || !entry.getValue().isJsonPrimitive())
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
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                        || !entry.getValue().isJsonPrimitive())
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
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                        || !entry.getValue().isJsonPrimitive())
                        continue;
                    inspectionRuleSeverityOverrides.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
    }
}
