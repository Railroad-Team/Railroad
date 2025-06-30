package io.github.railroad.project.facet.data;

import io.github.railroad.utility.JavaVersion;
import lombok.Data;

/**
 * Holds information about the Java configuration for a project facet.
 * Used by the Java facet to describe the Java version detected in the project.
 */
@Data
public class JavaFacetData {
    /**
     * The Java version used by the project.
     */
    private JavaVersion version;
}
