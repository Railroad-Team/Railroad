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
            //TODO: Create JSON based on settings tree
            //Split setting id by . and : and create a json object for each part
            //Once the last part is reached, create a json object of the type in the codec, and add the value
        }
        Railroad.LOGGER.debug("Settings JSON: {}", json);
        return null;
    }

    @Override
    public void fromJson(JsonObject json) {

    }
}
