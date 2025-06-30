package io.github.railroad.project.facet.data;

import lombok.Data;

/**
 * Holds information about the Gradle build configuration for a project facet.
 * Used by the Gradle facet to describe the build file, version, and script type.
 */
@Data
public class GradleFacetData {
    /**
     * The version of Gradle used by the project, if detected.
     */
    private String gradleVersion;
    /**
     * The path to the Gradle build file (build.gradle or build.gradle.kts).
     */
    private String buildFilePath;
    /**
     * True if the build file is a Kotlin script (build.gradle.kts), false if Groovy (build.gradle).
     */
    private boolean isKts;
}
