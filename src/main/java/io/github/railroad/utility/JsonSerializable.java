package io.github.railroad.utility;

import com.google.gson.JsonElement;
import io.github.railroad.Railroad;

public interface JsonSerializable<T extends JsonElement> {
    T toJson();

    void fromJson(T json);

    static String toString(JsonElement jsonObject) {
        return Railroad.GSON.toJson(jsonObject);
    }
}
