package dev.railroadide.railroad.project.creation.modjson.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.railroad.project.creation.modjson.Person;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts Fabric author and contributor arrays containing names or person objects.
 */
public class PersonListTypeAdapter extends TypeAdapter<List<Person>> {
    private final Gson gson = new Gson();

    /**
     * Reads people from an array, treating strings as names and skipping null entries.
     *
     * @param in the reader positioned at the array
     * @return the people in their input order
     * @throws IOException if reading fails or an entry has an unsupported JSON token
     */
    @Override
    public List<Person> read(JsonReader in) throws IOException {
        List<Person> people = new ArrayList<>();

        in.beginArray();
        while (in.hasNext()) {
            JsonToken token = in.peek();

            if (token == JsonToken.STRING) {
                // JSON is a single string (e.g., "username")
                String name = in.nextString();
                people.add(Person.fromName(name));
            } else if (token == JsonToken.BEGIN_OBJECT) {
                // JSON is an object (e.g., {"name": "username", "contact": {...}})
                Person person = this.gson.fromJson(in, Person.class);
                people.add(person);
            } else if (token == JsonToken.NULL) {
                in.nextNull(); // Skip null entries
            } else
                throw new IOException("Expected string, object, or null for person but got " + token);
        }
        in.endArray();

        return people;
    }

    /**
     * Writes people without contact information as names and other people as objects.
     * A null or empty list is written as JSON null.
     *
     * @param out the destination JSON writer
     * @param value the list of people to serialize, or {@code null}
     * @throws IOException if writing fails
     */
    @Override
    public void write(JsonWriter out, List<Person> value) throws IOException {
        if (value == null || value.isEmpty()) {
            out.nullValue();
            return;
        }

        out.beginArray();
        for (Person person : value) {
            if (person.getContact() == null) {
                // Write as a simple string if no contact info
                out.value(person.getName());
            } else {
                // Write as an object if contact info is present
                this.gson.toJson(person, Person.class, out);
            }
        }
        out.endArray();
    }
}
