package io.github.railroad.config;

import com.google.gson.JsonObject;
import io.github.railroad.core.utility.JsonSerializable;

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
