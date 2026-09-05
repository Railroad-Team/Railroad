package dev.railroadide.railroad.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An author or contributor named in Fabric mod metadata, with optional contact information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person {
    /** The person's required display name. */
    private String name; // Mandatory
    /** The person's contact information, or {@code null} if omitted. */
    private ContactInformation contact; // Optional

    /**
     * Creates a person entry containing only a display name.
     *
     * @param name the person's display name
     * @return a person with no contact information
     */
    public static Person fromName(String name) {
        return new Person(name, null);
    }
}
