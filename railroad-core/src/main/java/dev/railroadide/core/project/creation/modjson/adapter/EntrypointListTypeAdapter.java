package dev.railroadide.core.project.creation.modjson.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.core.project.creation.modjson.Entrypoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EntrypointListTypeAdapter extends TypeAdapter<List<Entrypoint>> {
    private final Gson gson = new Gson();

    @Override
    public List<Entrypoint> read(JsonReader in) throws IOException {
        List<Entrypoint> entrypoints = new ArrayList<>();

        in.beginArray();
        while (in.hasNext()) {
            JsonToken token = in.peek();

            if (token == JsonToken.STRING) {
                // Value is a simple string (e.g., "my.package.MyClass")
                String value = in.nextString();
                entrypoints.add(new Entrypoint(value, "default"));
            } else if (token == JsonToken.BEGIN_OBJECT) {
                // Value is an object (e.g., {"value": "my.package.MyClass", "adapter": "custom"})
                Entrypoint entrypoint = gson.fromJson(in, Entrypoint.class);
                if (entrypoint.getAdapter() == null) {
                    entrypoint.setAdapter("default"); // Default adapter if not specified
                }

                entrypoints.add(entrypoint);
            } else if (token == JsonToken.NULL) {
                in.nextNull();
            } else
                throw new IOException("Expected string, object, or null for Entrypoint but got " + token);
        }
        in.endArray();

        return entrypoints;
    }

    @Override
    public void write(JsonWriter out, List<Entrypoint> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginArray();
        for (Entrypoint entrypoint : value) {
            // Write back as a string if using the default adapter and only having a value
            if ("default".equals(entrypoint.getAdapter()) && entrypoint.getAdapter() != null) {
                out.value(entrypoint.getValue());
            } else {
                // Otherwise, write the full object
                out.beginObject();
                out.name("value").value(entrypoint.getValue());
                // Only write 'adapter' if it's not the default
                if (!"default".equals(entrypoint.getAdapter()) && entrypoint.getAdapter() != null) {
                    out.name("adapter").value(entrypoint.getAdapter());
                }
                out.endObject();
            }
        }
        out.endArray();
    }
}
