package dev.railroadide.core.project.creation.modjson.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.railroadide.core.project.creation.modjson.Person;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PersonListTypeAdapter extends TypeAdapter<List<Person>> {
    private final Gson gson = new Gson();

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
            } else {
                throw new IOException("Expected string, object, or null for person but got " + token);
            }
        }
        in.endArray();

        return people;
    }

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
