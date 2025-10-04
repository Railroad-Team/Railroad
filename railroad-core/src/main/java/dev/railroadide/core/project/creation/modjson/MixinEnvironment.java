package dev.railroadide.core.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MixinEnvironment {
    private String config; // Filename, mandatory
    private String environment; // Optional, e.g., "client" or "server"

    public static MixinEnvironment of(String config) {
        return new MixinEnvironment(config, null);
    }
}
