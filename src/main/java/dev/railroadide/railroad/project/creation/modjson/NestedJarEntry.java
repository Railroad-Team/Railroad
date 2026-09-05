package dev.railroadide.railroad.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identifies a nested JAR included in a Fabric mod's metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NestedJarEntry {
    /** The nested JAR's resource path within the containing mod. */
    private String file;
}
