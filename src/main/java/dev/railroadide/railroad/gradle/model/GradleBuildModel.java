package dev.railroadide.railroad.gradle.model;

import dev.railroadide.railroadplugin.dto.FabricDataModel;
import dev.railroadide.railroadplugin.dto.RailroadProject;

import java.nio.file.Path;

/**
 * Represents a Gradle build and its constituent projects.
 *
 * @param gradleVersion the version of Gradle used to build the project
 * @param rootDir the root directory of the imported build
 * @param fabricData the Fabric modding platform data model, if applicable
 * @param project the root project of the Gradle build
 */
public record GradleBuildModel(
    String gradleVersion,
    Path rootDir,
    FabricDataModel fabricData,
    RailroadProject project
) {
}
