package dev.railroadide.core.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person {
    private String name; // Mandatory
    private ContactInformation contact; // Optional

    public static Person fromName(String name) {
        return new Person(name, null);
    }
}
