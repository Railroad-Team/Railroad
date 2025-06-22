package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.JsonSerializable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class Settings implements JsonSerializable<JsonObject> {
    private final ObservableMap<String, Setting<?>> settings = FXCollections.observableHashMap();

    public void registerSetting(Setting<?> setting) {
        settings.put(setting.getId(), setting);
    }

    public Setting<?> getSetting(String id) {
        return settings.get(id);
    }

    /**
     * Reloads all settings by calling their apply method.
     */
    public void reloadSettings() {
        settings.values().forEach(setting -> {
            setting.getApplySetting().accept(null);
        });
    }

    /**
     * Converts the settings to a json object.
     * Loops through each setting, and then loops through each part in the ID.
     * A variable - current - is used to keep track of the current position, essentially acting as a pointer.
     * If the part is the last part, it will add the value to the json object.
     * If it is not the last part, it will either create a new json object if it does not exist, or move to the next json object.
     *
     * @return {@link JsonObject} The json object to be written to a file.
     */
    @Override
    public JsonObject toJson() {
        var json = new JsonObject();
        for (Setting<?> setting : settings.values()) {
            var parts = setting.getId().split("[.:]");
            var current = json;

            if (setting.getValue() == null) {
                setting.setValue(setting.getDefaultValue());
            }

            for (String part : parts) {
                if (Arrays.stream(parts).toList().indexOf(part) == parts.length - 1) {
                    current.add(part, Railroad.SETTINGS_HANDLER.getCodec(setting.getCodecId()).jsonEncoder().apply(setting.getValue()));
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

    /**
     * Converts the json object to settings.
     * Loops through each setting, and then loops through each part in the ID.
     * A variable - current - is used to keep track of the current position, essentially acting as a pointer.
     * If the part is the last part, it will add the value to the setting.
     * If the part is null, it will use the default value.
     * If it is not the last part, it will set current to the part.
     *
     * <p>
     * TODO rewrite this method, possibly? - I don't know why I wrote this comment, but I did.
     * (Probably because it's kinda uh, not the best way to do it?)
     * </p>
     *
     * @param json The json object to convert to settings.
     * @throws IllegalStateException If the setting cannot be decoded.
     */
    @Override
    public void fromJson(JsonObject json) throws IllegalStateException {
        for (Setting<?> setting : settings.values()) {
            String[] parts = setting.getId().split("[.:]");
            var position = json;

            for (String part : parts) {
                if (Arrays.stream(parts).toList().indexOf(part) == parts.length - 1) {
                    if (position.has(part)) {
                        @SuppressWarnings("unchecked")
                        SettingCodec<?, ?, JsonElement> codec = (SettingCodec<?, ?, JsonElement>) Railroad.SETTINGS_HANDLER.getCodec(setting.getCodecId());
                        Object value = codec.jsonDecoder().apply(position.get(part));

                        if (value == null) {
                            Railroad.LOGGER.error("Failed to decode setting {}", setting.getId());
                            setting.setValue(setting.getDefaultValue());
                        } else {
                            setting.setValue(value);
                        }
                    } else {
                        setting.setValue(setting.getDefaultValue());
                    }
                } else {
                    if (!position.has(part)) {
                        setting.setValue(setting.getDefaultValue());
                        break;
                    }

                    position = position.getAsJsonObject(part);
                }
            }
        }
    }
}
