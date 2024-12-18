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
        //Either loop through the json object tree and find each child JsonElement
        //Pros:
        //Can flag unknown settings, easier debugging?

        //Cons:
        //Slow

        //Or just search for each already defined setting in SettingsManager
        //Pros:
        //Faster

    }
}
