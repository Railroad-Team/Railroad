package dev.railroadide.railroad.gradle;

import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.project.Project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Represents the environment configuration for Gradle execution within a project.
 */
public interface GradleEnvironment {

    /**
     * Retrieves the associated project for this Gradle environment.
     *
     * @return the project instance.
     */
    Project project();

    /**
     * Determines whether the Gradle wrapper should be used.
     *
     * @return true if the Gradle wrapper is used, false otherwise.
     */
    boolean useWrapper();

    /**
     * Retrieves the installation path of the Gradle distribution, if specified.
     *
     * @return an Optional containing the installation path, or an empty Optional if not set.
     */
    Optional<Path> installationPath();

    /**
     * Retrieves the user home directory path for Gradle, if specified.
     *
     * @return an Optional containing the user home path, or an empty Optional if not set.
     */
    Optional<Path> userHomePath();

    /**
     * Retrieves the Java Development Kit (JDK) configuration for this Gradle environment, if specified.
     *
     * @return an Optional containing the JDK instance, or an empty Optional if not set.
     */
    Optional<JDK> jvm();

    /**
     * Constructs the JVM arguments for a specific Gradle task execution request.
     *
     * @param request the Gradle task execution request.
     * @param jvm     the JDK instance to be used.
     * @return a string containing the JVM arguments.
     */
    String jvmArgumentsFor(GradleTaskExecutionRequest request, JDK jvm);

    /**
     * Checks whether the Gradle daemon is enabled.
     *
     * @return true if the Gradle daemon is enabled, false otherwise.
     */
    boolean isDaemonEnabled();

    /**
     * Retrieves the idle timeout duration for the Gradle daemon, if specified.
     *
     * @return an Optional containing the idle timeout duration, or an empty Optional if not set.
     */
    Optional<Duration> daemonIdleTimeout();
}
