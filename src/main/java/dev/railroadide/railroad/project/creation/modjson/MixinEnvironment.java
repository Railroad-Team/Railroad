package dev.railroadide.railroad.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A mixin configuration resource and its optional target environment in Fabric metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MixinEnvironment {
    /** The required mixin configuration resource path. */
    private String config; // Filename, mandatory
    /** The target environment, or {@code null} for an unrestricted configuration. */
    private String environment; // Optional, e.g., "client" or "server"

    /**
     * Creates a mixin configuration reference without an environment restriction.
     *
     * @param config the mixin configuration resource path
     * @return the unrestricted configuration reference
     */
    public static MixinEnvironment of(String config) {
        return new MixinEnvironment(config, null);
    }
}
