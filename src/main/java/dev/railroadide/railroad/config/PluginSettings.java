package dev.railroadide.railroad.config;

import com.google.gson.JsonObject;
import dev.railroadide.core.utility.JsonSerializable;

/**
 * An interface that allows for the settings of a plugin to be saved and loaded.
 */
public interface PluginSettings extends JsonSerializable<JsonObject> {
    @Override
    default JsonObject toJson() {
        return new JsonObject();
    }

    @Override
    default void fromJson(JsonObject json) {

    }
}
