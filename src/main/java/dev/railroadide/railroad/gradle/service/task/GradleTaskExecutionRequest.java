package dev.railroadide.railroad.gradle.service.task;

import dev.railroadide.railroad.gradle.service.GradleConsoleMode;

import java.util.List;
import java.util.Map;

/**
 * Describes how Gradle should execute a specific task.
 *
 * @param taskPath            the fully-qualified task path to run
 * @param additionalArgs      extra CLI arguments provided by the user
 * @param systemProperties    Gradle system properties that should be passed to the JVM
 * @param environment         environment variables to include with the Gradle process
 * @param offline             whether Gradle should operate in offline mode
 * @param refreshDependencies whether Gradle should refresh dependencies before running
 * @param debug               whether to enable Gradle debug logging
 * @param consoleMode         how Gradle should render console output
 */
public record GradleTaskExecutionRequest(String taskPath, List<String> additionalArgs,
                                         Map<String, String> systemProperties, Map<String, String> environment,
                                         boolean offline, boolean refreshDependencies, boolean debug,
                                         GradleConsoleMode consoleMode) {
}
