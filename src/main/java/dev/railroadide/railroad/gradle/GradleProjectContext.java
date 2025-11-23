package dev.railroadide.railroad.gradle;

import dev.railroadide.railroad.gradle.service.GradleExecutionService;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import org.gradle.tooling.model.build.GradleEnvironment;

import java.nio.file.Path;

/**
 * Provides access to shared Gradle resources for a given project.
 */
public interface GradleProjectContext {

    /**
     * @return the root directory of the Gradle project.
     */
    Path getProjectDir();

    /**
     * @return the Gradle environment that describes the current toolchain.
     */
    GradleEnvironment getEnvironment();

    /**
     * @return the model service that can refresh and cache Gradle metadata.
     */
    GradleModelService getModelService();

    /**
     * @return the execution service used to run Gradle tasks within this project.
     */
    GradleExecutionService getExecutionService();

    /**
     * @return the settings that configure how Gradle is invoked for this project.
     */
    GradleSettings getSettings();
}
