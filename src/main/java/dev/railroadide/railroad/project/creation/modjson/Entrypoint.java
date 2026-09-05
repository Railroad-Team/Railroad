package dev.railroadide.railroad.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Fabric entrypoint value and the language adapter used to resolve it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Entrypoint {
    /** The entrypoint reference interpreted by the language adapter. */
    private String value;
    /** The language adapter identifier. */
    private String adapter;

    /**
     * Creates an entrypoint that uses the default language adapter.
     *
     * @param value the entrypoint reference
     * @return an entrypoint with adapter {@code default}
     */
    public static Entrypoint of(String value) {
        return new Entrypoint(value, "default");
    }
}
