package dev.railroadide.railroad.project.creation.modjson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.railroad.project.creation.modjson.VersionRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts Fabric version expressions between string or string-array JSON and {@link VersionRange}.
 */
public class VersionRangeTypeAdapter extends TypeAdapter<VersionRange> {

    /**
     * Reads one version expression or an array of expressions without interpreting them.
     *
     * @param in the reader positioned at a string, array, or null
     * @return the version range, or {@code null} for JSON null
     * @throws IOException if reading fails or the JSON token is unsupported
     */
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

    /**
     * Writes a single expression as a string and zero or multiple expressions as an array.
     * A null range or null expression list is written as JSON null.
     *
     * @param out the destination JSON writer
     * @param value the version range to serialize, or {@code null}
     * @throws IOException if writing fails
     */
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
