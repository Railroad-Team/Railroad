package dev.railroadide.core.project.creation.modjson.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.core.project.creation.modjson.MixinEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MixinListTypeAdapter extends TypeAdapter<List<MixinEnvironment>> {
    private final Gson gson = new Gson();

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
            } else {
                throw new IOException("Expected string, object, or null for Mixin entry but got " + token);
            }
        }

        in.endArray();
        return configs;
    }

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
