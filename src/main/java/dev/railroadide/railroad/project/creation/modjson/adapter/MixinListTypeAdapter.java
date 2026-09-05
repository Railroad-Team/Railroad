package dev.railroadide.railroad.project.creation.modjson.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.railroad.project.creation.modjson.MixinEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts Fabric mixin arrays containing resource paths or configuration objects.
 */
public class MixinListTypeAdapter extends TypeAdapter<List<MixinEnvironment>> {
    private final Gson gson = new Gson();

    /**
     * Reads mixin configurations, treating string paths as unrestricted and skipping null entries.
     *
     * @param in the reader positioned at the array
     * @return the mixin configurations in their input order
     * @throws IOException if reading fails or an entry has an unsupported JSON token
     */
    @Override
    public List<MixinEnvironment> read(JsonReader in) throws IOException {
        List<MixinEnvironment> configs = new ArrayList<>();

        in.beginArray();

        while (in.hasNext()) {
            JsonToken token = in.peek();

            if (token == JsonToken.STRING) {
                // Value is a simple string (filename)
                String filename = in.nextString();
                configs.add(new MixinEnvironment(filename, null));
            } else if (token == JsonToken.BEGIN_OBJECT) {
                // Value is a MixinEnvironment object
                MixinEnvironment configObject = gson.fromJson(in, MixinEnvironment.class);
                configs.add(configObject);
            } else if (token == JsonToken.NULL) {
                in.nextNull();
            } else
                throw new IOException("Expected string, object, or null for Mixin entry but got " + token);
        }

        in.endArray();
        return configs;
    }

    /**
     * Writes unrestricted mixin configurations as strings and environment-specific ones as objects.
     * A null list is written as JSON null.
     *
     * @param out the destination JSON writer
     * @param value the configuration list to serialize, or {@code null}
     * @throws IOException if writing fails
     */
    @Override
    public void write(JsonWriter out, List<MixinEnvironment> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginArray();
        for (MixinEnvironment config : value) {
            // Write back as a string if it only has the filename and no environment key
            if (config.getEnvironment() == null) {
                out.value(config.getConfig());
            } else {
                // Otherwise, write the full object
                gson.toJson(config, MixinEnvironment.class, out);
            }
        }
        out.endArray();
    }
}
