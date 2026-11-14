package dev.railroadide.railroad.ide.sst.graph.internal;

import com.google.gson.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/** Internal codec for the disposable index snapshot; record constructors validate loaded facts. */
public final class SemanticGraphCodec {
    private static final int MAXIMUM_RECORD_BYTES = 64 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(DocumentId.class, new DocumentIdAdapter())
        .registerTypeAdapter(DocumentUri.class, new DocumentUriAdapter())
        .registerTypeHierarchyAdapter(Location.class, new LocationAdapter())
        .create();

    private SemanticGraphCodec() {
    }

    public static void write(DataOutput output, Object value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_RECORD_BYTES)
            throw new IOException("Semantic index record exceeds cache size limit");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    public static <T> T read(DataInput input, Class<T> type) throws IOException {
        int length = input.readInt();
        if (length == -1)
            return null;
        if (length < 0 || length > MAXIMUM_RECORD_BYTES)
            throw new IOException("Invalid semantic index record length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try {
            return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
        } catch (JsonParseException exception) {
            throw new IOException("Invalid semantic index record", exception);
        }
    }

    private static final class DocumentIdAdapter implements JsonSerializer<DocumentId>, JsonDeserializer<DocumentId> {
        @Override
        public JsonElement serialize(DocumentId value, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(value.toString());
        }

        @Override
        public DocumentId deserialize(JsonElement value, Type type, JsonDeserializationContext context) {
            return DocumentId.parse(value.getAsString());
        }
    }

    private static final class DocumentUriAdapter
        implements
            JsonSerializer<DocumentUri>,
            JsonDeserializer<DocumentUri> {
        @Override
        public JsonElement serialize(DocumentUri value, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(value.toString());
        }

        @Override
        public DocumentUri deserialize(JsonElement value, Type type, JsonDeserializationContext context) {
            return DocumentUri.parse(value.getAsString());
        }
    }

    private static final class LocationAdapter implements JsonSerializer<Location<?>>, JsonDeserializer<Location<?>> {
        @Override
        public JsonElement serialize(Location<?> value, Type type, JsonSerializationContext context) {
            var json = new JsonObject();
            json.addProperty("id", value.documentId().toString());
            json.addProperty("version", value.documentVersion().value());
            json.addProperty("start", value.range().start);
            json.addProperty("end", value.range().end);
            json.addProperty("text", value instanceof Location.TextLocation);
            if (value instanceof Location.TextLocation text) {
                json.addProperty("line", text.line());
                json.addProperty("column", text.column());
            }
            return json;
        }

        @Override
        public Location<?> deserialize(JsonElement value, Type type, JsonDeserializationContext context) {
            var json = value.getAsJsonObject();
            var id = DocumentId.parse(json.get("id").getAsString());
            var version = new DocumentVersion(json.get("version").getAsLong());
            int start = json.get("start").getAsInt();
            int end = json.get("end").getAsInt();
            return json.get("text").getAsBoolean()
                ? new Location.TextLocation(id, version, new TextRange(start, end),
                    json.get("line").getAsInt(), json.get("column").getAsInt())
                : new Location.BinaryLocation(id, version, new BinaryRange(start, end));
        }
    }
}
