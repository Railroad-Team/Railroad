package dev.railroadide.core.project.creation.modjson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StringOrStringArrayTypeAdapter extends TypeAdapter<List<String>> {
    @Override
    public List<String> read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.STRING) {
            // JSON is a single string (e.g., "client")
            return Collections.singletonList(in.nextString());
        } else if (token == JsonToken.BEGIN_ARRAY) {
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
