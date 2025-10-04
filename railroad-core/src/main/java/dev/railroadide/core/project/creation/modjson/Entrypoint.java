package dev.railroadide.core.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Entrypoint {
    private String value;
    private String adapter;

    public static Entrypoint of(String value) {
        return new Entrypoint(value, null);
    }
}
