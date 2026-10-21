package dev.railroadide.railroad.project.facet;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Serializes facets as an identifier and data object, resolving identifiers through {@link FacetManager} when reading.
 */
public class FacetTypeAdapter extends TypeAdapter<Facet<?>> {
    /**
     * Gson instance used to serialize and deserialize facet data without this adapter.
     */
    public static final Gson DEFAULT_GSON = new Gson();
    private static final String FIELD_ID = "id";
    private static final String FIELD_DATA = "data";

    /**
     * Writes the facet identifier and its data using the registered data class.
     *
     * @param out the destination JSON writer
     * @param facet the facet to serialize
     * @throws IOException if writing the JSON fails
     */
    @Override
    public void write(JsonWriter out, Facet<?> facet) throws IOException {
        out.beginObject();
        out.name(FIELD_ID).value(facet.getType().id());
        out.name(FIELD_DATA);

        // delegate to Gson’s default for the data object
        DEFAULT_GSON.toJson(facet.getData(), facet.getType().dataClass(), out);
        out.endObject();
    }

    /**
     * Reads a facet, skipping unknown fields and resolving its data class from the registered type.
     *
     * @param in the source JSON reader
     * @return the facet with its deserialized data
     * @throws IOException if reading the JSON fails
     * @throws JsonParseException if the identifier is missing or is not registered
     */
    @SuppressWarnings("unchecked")
    @Override
    public Facet<?> read(JsonReader in) throws IOException {
        String id = null;
        JsonElement dataElem = null;

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case FIELD_ID :
                    id = in.nextString();
                    break;
                case FIELD_DATA :
                    dataElem = JsonParser.parseReader(in);
                    break;
                default :
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
