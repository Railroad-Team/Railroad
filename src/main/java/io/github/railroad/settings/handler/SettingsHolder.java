package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.core.settings.Setting;
import io.github.railroad.utility.JsonSerializable;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * The SettingsHolder class manages a collection of settings,
 * allowing for registration, retrieval, and JSON serialization/deserialization.
 * Each setting must have a unique ID and can be of any type.
 */
@Getter
public class SettingsHolder implements JsonSerializable<JsonObject> {
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
                JsonElement element = value.toJson();
                if (element != null) {
                    json.add(key, element);
                } else Railroad.LOGGER.error("Setting with ID '{}' returned null from toJson().", key);
            } else Railroad.LOGGER.error("Setting with ID '{}' is null.", key);
        }

        return json;
    }

    @Override
    public void fromJson(JsonObject json) throws IllegalStateException {
        if (json == null)
            throw new IllegalArgumentException("JSON object cannot be null");

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            Setting<?> setting = SettingsHandler.SETTINGS_REGISTRY.get(key);
            if (setting != null) {
                setting.fromJson(value);
            } else {
                Railroad.LOGGER.error("Setting with ID '{}' does not exist in the settings collection.", key);
            }
        }
    }

    public List<Setting<?>> getSettings() {
        return List.copyOf(SettingsHandler.SETTINGS_REGISTRY.values());
    }
}
