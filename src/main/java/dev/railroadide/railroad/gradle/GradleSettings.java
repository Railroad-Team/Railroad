package dev.railroadide.railroad.gradle;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.java.JDK;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.nio.file.Path;
import java.util.List;

/**
 * Captures the CLI and project settings that should be applied when invoking Gradle.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class GradleSettings {
    private boolean useWrapper;
    private String wrapperVersion;
    private Path customGradleHome;
    private Path gradleUserHome;
    private JDK gradleJvm;
    private boolean offlineMode;
    private boolean enableBuildCache;
    private boolean parallelExecution;
    private int maxWorkerCount;
    private List<RunConfiguration<?>> configurations;
    private boolean daemonEnabled;
    private Long daemonIdleTimeout;

    /**
     * Constructs a new GradleSettings instance with the specified settings.
     *
     * @param useWrapper whether to use the Gradle wrapper
     * @param wrapperVersion the version of the Gradle wrapper to use
     * @param customGradleHome the custom Gradle home directory
     * @param gradleUserHome the Gradle user home directory
     * @param gradleJvm the JDK to use for Gradle
     * @param offlineMode whether to enable offline mode
     * @param enableBuildCache whether to enable the build cache
     * @param parallelExecution whether to enable parallel execution
     * @param maxWorkerCount the maximum number of worker threads for parallel execution
     * @param configurations the list of run configurations to apply
     * @param daemonEnabled whether to enable the Gradle daemon
     * @param daemonIdleTimeout the idle timeout for the Gradle daemon in milliseconds
     */
    public GradleSettings(
        boolean useWrapper,
        String wrapperVersion,
        Path customGradleHome,
        Path gradleUserHome,
        JDK gradleJvm,
        boolean offlineMode,
        boolean enableBuildCache,
        boolean parallelExecution,
        int maxWorkerCount,
        List<RunConfiguration<?>> configurations,
        boolean daemonEnabled,
        Long daemonIdleTimeout
    ) {
        this.useWrapper = useWrapper;
        this.wrapperVersion = wrapperVersion;
        this.customGradleHome = customGradleHome;
        this.gradleUserHome = gradleUserHome;
        this.gradleJvm = gradleJvm;
        this.offlineMode = offlineMode;
        this.enableBuildCache = enableBuildCache;
        this.parallelExecution = parallelExecution;
        this.maxWorkerCount = maxWorkerCount;
        this.configurations = configurations;
        this.daemonEnabled = daemonEnabled;
        this.daemonIdleTimeout = daemonIdleTimeout;
    }
}
