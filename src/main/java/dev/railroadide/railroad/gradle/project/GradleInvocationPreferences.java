package dev.railroadide.railroad.gradle.project;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Preferences for invoking Gradle builds.
 *
 * @param offlineMode              whether to run Gradle in offline mode
 * @param enableBuildCache         whether to enable the Gradle build cache
 * @param parallelExecution        whether to enable parallel execution of tasks
 * @param isDaemonEnabled          whether to enable the Gradle daemon
 * @param daemonIdleTimeoutMinutes the idle timeout for the Gradle daemon in minutes
 * @param maxWorkerCount           the maximum number of worker threads for Gradle
 * @param customGradleHome         a custom Gradle home directory, or null to use the default
 * @param gradleUserHome           a custom Gradle user home directory, or null to use the default
 */
public record GradleInvocationPreferences(boolean offlineMode,
                                          boolean enableBuildCache,
                                          boolean parallelExecution,
                                          boolean isDaemonEnabled,
                                          Long daemonIdleTimeoutMinutes,
                                          Integer maxWorkerCount,
                                          Path customGradleHome,
                                          Path gradleUserHome) {
    /**
     * Returns the default Gradle invocation preferences.
     * <p>
     * Defaults:
     * - offlineMode: false
     * - enableBuildCache: false
     * - parallelExecution: false
     * - isDaemonEnabled: true
     * - daemonIdleTimeoutMinutes: null
     * - maxWorkerCount: null
     * - customGradleHome: null
     * - gradleUserHome: null
     *
     * @return the default preferences
     */
    static GradleInvocationPreferences defaults() {
        return new GradleInvocationPreferences(false, false, false, true, null, null, null, null);
    }

    /**
     * Returns the daemon idle timeout as a Duration.
     *
     * @return the daemon idle timeout, or null if not set
     */
    Duration daemonIdleTimeout() {
        return daemonIdleTimeoutMinutes == null ? null : Duration.ofMinutes(daemonIdleTimeoutMinutes);
    }
}
