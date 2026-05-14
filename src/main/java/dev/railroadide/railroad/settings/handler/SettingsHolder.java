package dev.railroadide.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Setting;
import dev.railroadide.railroad.utility.json.JsonSerializable;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The SettingsHolder class manages a collection of settings,
 * allowing for registration, retrieval, and JSON serialization/deserialization.
 * Each setting must have a unique ID and can be of any type.
 */
@Getter
public class SettingsHolder implements JsonSerializable<JsonObject> {
    private final Map<String, JsonElement> pendingSettings = new HashMap<>();

    public void updateAll() {
        for (Map.Entry<String, Setting<?>> entry : SettingsHandler.SETTINGS_REGISTRY.entries().entrySet()) {
            String id = entry.getKey();
            Setting<?> setting = entry.getValue();

            if (setting != null) {
                setting.forceUpdate();
            } else {
                Railroad.LOGGER.error("Setting with ID '{}' is null, removing it from the settings collection.", id);
            }
        }
    }

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();

        for (Map.Entry<String, Setting<?>> entry : SettingsHandler.SETTINGS_REGISTRY.entries().entrySet()) {
            String key = entry.getKey();
            Setting<?> value = entry.getValue();

            if (value != null) {
                if (!value.isPersisted())
                    continue;

                JsonElement element = value.toJson();
                if (element != null) {
                    json.add(key, element);
                } else Railroad.LOGGER.error("Setting with ID '{}' returned null from toJson().", key);
            } else Railroad.LOGGER.error("Setting with ID '{}' is null.", key);
        }

        for (Map.Entry<String, JsonElement> entry : pendingSettings.entrySet()) {
            if (!json.has(entry.getKey())) {
                json.add(entry.getKey(), entry.getValue());
            }
        }

        return json;
    }

    @Override
    public void fromJson(JsonObject json) throws IllegalStateException {
        if (json == null)
            throw new IllegalArgumentException("JSON object cannot be null");

        pendingSettings.clear();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            Setting<?> setting = SettingsHandler.SETTINGS_REGISTRY.get(key);
            if (setting != null) {
                if (setting.isPersisted()) {
                    setting.fromJson(value);
                } else {
                    Railroad.LOGGER.debug("Setting with ID '{}' is non-persisted; ignoring stored value.", key);
                }
            } else {
                pendingSettings.put(key, value);
                Railroad.LOGGER.debug("Setting with ID '{}' is not registered yet, deferring value load.", key);
            }
        }
    }

    public void tryHydratePendingSetting(String key, Setting<?> setting) {
        if (key == null || key.isBlank() || setting == null)
            return;

        JsonElement pendingValue = pendingSettings.remove(key);
        if (pendingValue != null) {
            setting.fromJson(pendingValue);
        }
    }

    public List<Setting<?>> getSettings() {
        return List.copyOf(SettingsHandler.SETTINGS_REGISTRY.values());
    }
}
