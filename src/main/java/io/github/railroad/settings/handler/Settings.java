package io.github.railroad.settings.handler;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.JsonSerializable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.Getter;

import java.util.Arrays;

public class Settings implements JsonSerializable<JsonObject> {
    @Getter
    private final ObservableMap<String, Setting> settings = FXCollections.observableHashMap();

    public void registerSetting(Setting setting) {
        settings.put(setting.getId(), setting);
    }

    public Setting getSetting(String id) {
        return settings.get(id);
    }

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();
        for (Setting setting : settings.values()) {
            var parts = setting.getId().split("[.:]");
            var current = json;

            if (setting.getValue() == null) {
                setting.setValue(setting.getDefaultValue());
            }

            for(String part : parts) {
                if (Arrays.stream(parts).toList().indexOf(part) == parts.length - 1) {
                    current.add(part, Railroad.SETTINGS_HANDLER.getCodec(setting.getCodecId()).getJsonEncoder().apply(setting.getValue()));
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
        for (Setting setting : settings.values()) {
            var parts = setting.getId().split("[.:]");
            var current = json;

            for (String part : parts) {
                if (Arrays.stream(parts).toList().indexOf(part) == parts.length - 1) {
                    if (current.has(part)) {
                        var value = Railroad.SETTINGS_HANDLER.getCodec(setting.getCodecId()).getJsonDecoder().apply(current.get(part));

                        if (value == null) {
                            Railroad.LOGGER.error("Failed to decode setting " + setting.getId());
                            setting.setValue(setting.getDefaultValue());
                        } else {
                            setting.setValue(value);
                        }
                    } else {
                        setting.setValue(setting.getDefaultValue());
                    }
                } else {
                    if (!current.has(part)) {
                        setting.setValue(setting.getDefaultValue());
                        break;
                    }

                    current = current.getAsJsonObject(part);
                }
            }
        }
    }
}
