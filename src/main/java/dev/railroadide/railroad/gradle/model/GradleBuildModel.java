package dev.railroadide.railroad.gradle.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents a Gradle build and its constituent projects.
 *
 * @param gradleVersion the version of Gradle used to build the project
 * @param rootDir       the root directory of the imported build
 * @param projects      the list of projects contained in the build
 */
public record GradleBuildModel(String gradleVersion, Path rootDir, List<GradleProjectModel> projects) {
}
