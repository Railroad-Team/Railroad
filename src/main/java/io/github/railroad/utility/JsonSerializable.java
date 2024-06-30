package io.github.railroad.utility;

import com.google.gson.JsonElement;
import io.github.railroad.Railroad;

public interface JsonSerializable<T extends JsonElement> {
    static String toString(JsonElement jsonObject) {
        return Railroad.GSON.toJson(jsonObject);
    }

    T toJson();

    void fromJson(T json);
}
