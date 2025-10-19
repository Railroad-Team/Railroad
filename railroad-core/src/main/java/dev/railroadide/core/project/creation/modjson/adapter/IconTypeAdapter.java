package dev.railroadide.core.project.creation.modjson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.core.project.creation.modjson.IconInfo;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IconTypeAdapter extends TypeAdapter<IconInfo> {
    @Override
    public IconInfo read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.STRING) {
            // Value is a single string path
            String path = in.nextString();
            // our convention is to store this as a map with a single "default" key.
            Map<String, String> iconMap = Collections.singletonMap("default", path);
            return new IconInfo(iconMap);
        } else if (token == JsonToken.BEGIN_OBJECT) {
            // Value is a width -> path dictionary
            Map<String, String> iconMap = new HashMap<>();
            in.beginObject();
            while (in.hasNext()) {
                String width = in.nextName();
                String path = in.nextString();
                iconMap.put(width, path);
            }
            in.endObject();

            return new IconInfo(iconMap);
        } else if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        throw new IOException("Expected string, object, or null for icon field but got " + token);
    }

    @Override
    public void write(JsonWriter out, IconInfo value) throws IOException {
        if (value == null || value.getIconsByWidth() == null || value.getIconsByWidth().isEmpty()) {
            out.nullValue();
            return;
        }

        Map<String, String> iconMap = value.getIconsByWidth();

        // If there's only a single "default" entry, write it as a string
        if (iconMap.size() == 1 && iconMap.containsKey("default")) {
            out.value(iconMap.get("default"));
        } else {
            // Otherwise, write the full object map
            out.beginObject();
            for (Map.Entry<String, String> entry : iconMap.entrySet()) {
                out.name(entry.getKey()).value(entry.getValue());
            }
            out.endObject();
        }
    }
}
