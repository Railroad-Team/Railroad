package io.github.railroad.config;

import com.google.gson.JsonObject;
import io.github.railroad.utility.JsonSerializable;

//TODO unsure what to put for a javadoc here
public interface PluginSettings extends JsonSerializable<JsonObject> {
    @Override
    default JsonObject toJson() {
        return new JsonObject();
    }

    @Override
    default void fromJson(JsonObject json) {

    }
}
