package io.github.railroad.settings.handler;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.JsonSerializable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

public class Settings implements JsonSerializable<JsonObject> {
    private final ObservableMap<String, Setting<?>> settingsMap = FXCollections.observableHashMap();

    public ObservableMap<String, Setting<?>> getSettingsMap() {
        return settingsMap;
    }

    public void registerSetting(Setting<?> setting) {
        Railroad.LOGGER.info("Registering setting: {}", setting.getId());
        settingsMap.put(setting.getId(), setting);
    }

    public Setting getSetting(String id) {
        return settingsMap.get(id);
    }

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();
        for (Setting setting : settingsMap.values()) {
            var parts = setting.getId().split("[.:]");
            var current = json;
            if (setting.getValue() == null) {
                setting.setValue(setting.getDefaultValue());
            }
            for (int i = 0; i < parts.length; i++) {
                var part = parts[i];
                if (i == parts.length - 1) {
                    current.add(part, Railroad.SETTINGS_MANAGER.getCodec(setting.getType()).getJsonEncoder().apply(setting.getValue()));
                } else {
                    if (!current.has(part)) {
                        current.add(part, new JsonObject());
                    }
                    current = current.getAsJsonObject(part);
                }
            }
        }
        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        //TODO create a method to load settings from a JsonObject

        //For each setting in settingsMap, find the setting and its value in the json object
        //then set the value of the setting in the settingsMap to the value in the json object
        //If the setting is not found in the json object, set the value to the default value

        for (Setting setting : settingsMap.values()) {
            var parts = setting.getId().split("[.:]");
            var current = json;
            for (int i = 0; i < parts.length; i++) {
                var part = parts[i];
                if (i == parts.length - 1) {
                    if (current.has(part)) {
                        var val = Railroad.SETTINGS_MANAGER.getCodec(setting.getType()).getJsonDecoder().apply(current.get(part));
                        if (val == null) {
                            Railroad.LOGGER.error("Failed to decode setting {} value, setting to default", setting.getId());
                            setting.setValue(setting.getDefaultValue());
                            break;
                        }
                        setting.setValue(val);
                        Railroad.LOGGER.debug("Setting {} value set to {}", setting.getId(), setting.getValue());
                    } else {
                        setting.setValue(setting.getDefaultValue());
                        Railroad.LOGGER.debug("Setting {} value set to default value", setting.getId());
                    }
                } else {
                    if (!current.has(part)) {
                        setting.setValue(setting.getDefaultValue());
                        Railroad.LOGGER.debug("Setting {} not found in config, setting to default value instead.", setting.getId());
                        break;
                    }
                    current = current.getAsJsonObject(part);
                }
            }
        }
    }
}
