package io.github.railroad.project.facet;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class FacetTypeAdapter extends TypeAdapter<Facet<?>> {
    public static final Gson DEFAULT_GSON = new Gson();
    private static final String FIELD_ID = "id";
    private static final String FIELD_DATA = "data";

    @Override
    public void write(JsonWriter out, Facet<?> facet) throws IOException {
        out.beginObject();
        out.name(FIELD_ID).value(facet.getType().id());
        out.name(FIELD_DATA);

        // delegate to Gson’s default for the data object
        DEFAULT_GSON.toJson(facet.getData(), facet.getType().dataClass(), out);
        out.endObject();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Facet<?> read(JsonReader in) throws IOException {
        String id = null;
        JsonElement dataElem = null;

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case FIELD_ID:
                    id = in.nextString();
                    break;
                case FIELD_DATA:
                    dataElem = JsonParser.parseReader(in);
                    break;
                default:
                    in.skipValue();
            }
        }

        in.endObject();

        if (id == null)
            throw new JsonParseException("Facet missing 'id' field");

        FacetType<?> type = FacetManager.getType(id);
        if (type == null)
            throw new JsonParseException("Unknown facet id: " + id);

        // delegate to Gson’s default for the data object
        Object data = DEFAULT_GSON.fromJson(dataElem, type.dataClass());
        return new Facet<>((FacetType<Object>) type, data);
    }
}
