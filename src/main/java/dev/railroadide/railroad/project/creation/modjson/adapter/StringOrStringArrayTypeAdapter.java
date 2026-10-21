package dev.railroadide.railroad.project.creation.modjson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts metadata that permits either a single string or an array of strings.
 */
public class StringOrStringArrayTypeAdapter extends TypeAdapter<List<String>> {
    /**
     * Reads a string as a singleton list or reads all values from an array.
     *
     * @param in the reader positioned at a string, array, or null
     * @return the string values, or {@code null} for JSON null
     * @throws IOException if reading fails or the JSON token is unsupported
     */
    @Override
    public List<String> read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.STRING)
            // JSON is a single string (e.g., "client")
            return Collections.singletonList(in.nextString());
        else if (token == JsonToken.BEGIN_ARRAY) {
            // JSON is an array of strings (e.g., ["client", "server"])
            in.beginArray();
            List<String> list = new ArrayList<>();
            while (in.hasNext()) {
                list.add(in.nextString());
            }
            in.endArray();

            return list;

        } else if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        throw new IOException("Expected string or array for environment/license but got " + token);
    }

    /**
     * Writes one value as a string and multiple values as an array.
     * A null or empty list is written as JSON null.
     *
     * @param out the destination JSON writer
     * @param value the string values to serialize, or {@code null}
     * @throws IOException if writing fails
     */
    @Override
    public void write(JsonWriter out, List<String> value) throws IOException {
        if (value == null || value.isEmpty()) {
            out.nullValue();
            return;
        }

        // Write back as a single string if only one element, or array if multiple.
        if (value.size() == 1) {
            out.value(value.getFirst());
        } else {
            out.beginArray();
            for (String str : value) {
                out.value(str);
            }

            out.endArray();
        }
    }
}
