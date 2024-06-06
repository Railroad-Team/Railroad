package io.github.railroad.config;

import com.google.gson.JsonObject;
import io.github.railroad.utility.JsonSerializable;

public interface PluginSettings extends JsonSerializable<JsonObject> {
    @Override
    default JsonObject toJson() {
        return new JsonObject();
    }

    @Override
    default void fromJson(JsonObject json) {

    }
}
