package io.github.railroad.project.facet.data;

import lombok.Data;

/**
 * Holds information about the Maven build configuration for a project facet.
 * Used by the Maven facet to describe the POM file and Maven coordinates.
 */
@Data
public class MavenFacetData {
    /**
     * The path to the Maven POM file (pom.xml).
     */
    private String pomFilePath;
    /**
     * The Maven groupId of the project.
     */
    private String groupId;
    /**
     * The Maven artifactId of the project.
     */
    private String artifactId;
    /**
     * The Maven version of the project.
     */
    private String version;
}
