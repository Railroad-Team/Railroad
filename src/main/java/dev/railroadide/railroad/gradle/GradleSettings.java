package dev.railroadide.railroad.gradle;

import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.java.JDK;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Captures the CLI and project settings that should be applied when invoking Gradle.
 *
 * @param useWrapper        whether to force Gradle to use the project wrapper
 * @param wrapperVersion    the wrapper version that should be used when {@code useWrapper} is {@code true}
 * @param customGradleHome  overrides the location of the Gradle distribution to use
 * @param gradleUserHome    overrides the Gradle user home directory
 * @param gradleJvm         the JVM definition that Gradle should run under
 * @param offlineMode       whether Gradle should perform builds without network access
 * @param enableBuildCache  whether the shared Gradle build cache should be enabled
 * @param parallelExecution whether Gradle should allow parallel project execution
 * @param maxWorkerCount    the maximum number of worker threads Gradle may spawn
 * @param configurations    run configurations that may influence how Gradle is executed
 * @param isDaemonEnabled   whether the Gradle daemon should be enabled
 * @param daemonIdleTimeout the duration of inactivity after which the Gradle daemon should shut down
 */
public record GradleSettings(boolean useWrapper, String wrapperVersion, Path customGradleHome, Path gradleUserHome,
                             JDK gradleJvm, boolean offlineMode, boolean enableBuildCache, boolean parallelExecution,
                             int maxWorkerCount, List<RunConfiguration<?>> configurations,
                             boolean isDaemonEnabled, Duration daemonIdleTimeout) {
}
