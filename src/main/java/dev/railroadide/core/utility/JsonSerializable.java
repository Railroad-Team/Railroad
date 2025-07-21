package dev.railroadide.core.utility;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * An interface for objects that can be serialized to and deserialized from JSON.
 *
 * @param <T> The type of JSON element this object can be serialized to and deserialized from.
 */
public interface JsonSerializable<T extends JsonElement> {

    /**
     * Converts a given JSON element to its string representation using the provided Gson instance.
     *
     * @param gson       The Gson instance used for serialization.
     * @param jsonObject The JSON element to convert to a string.
     * @return The string representation of the JSON element.
     */
    static String toString(Gson gson, JsonElement jsonObject) {
        return gson.toJson(jsonObject);
    }

    /**
     * Serializes the current object to a JSON element.
     *
     * @return The JSON representation of the object.
     */
    T toJson();

    /**
     * Deserializes the given JSON element to populate the current object.
     *
     * @param json The JSON element to deserialize.
     */
    void fromJson(T json);
}