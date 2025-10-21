package dev.railroadide.core.project.creation.modjson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.core.project.creation.modjson.VersionRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VersionRangeTypeAdapter extends TypeAdapter<VersionRange> {

    @Override
    public VersionRange read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.STRING) {
            // Single string (e.g., ">=1.0.0 <2.0.0")
            String rangeStr = in.nextString();
            return new VersionRange(Collections.singletonList(rangeStr));
        } else if (token == JsonToken.BEGIN_ARRAY) {
            // Array of strings (e.g., ["1.0.0", "2.0.0"])
            List<String> list = new ArrayList<>();
            in.beginArray();

            while (in.hasNext()) {
                list.add(in.nextString());
            }

            in.endArray();
            return new VersionRange(list);

        } else if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        throw new IOException("Expected string or array for version range but got " + token);
    }

    @Override
    public void write(JsonWriter out, VersionRange value) throws IOException {
        if (value == null || value.getRanges() == null) {
            out.nullValue();
        } else if (value.getRanges().size() == 1) {
            out.value(value.getRanges().getFirst());
        } else {
            out.beginArray();
            for (String range : value.getRanges()) {
                out.value(range);
            }

            out.endArray();
        }
    }
}
