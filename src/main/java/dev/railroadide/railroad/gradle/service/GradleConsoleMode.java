package dev.railroadide.railroad.gradle.service;

/**
 * Configures how Gradle should render console output.
 */
public enum GradleConsoleMode {
    /**
     * Renders the console output in a rich format, with colors and other formatting enhancements.
     * This mode is useful for interactive terminals that support rich output.
     */
    RICH,
    /**
     * Renders the console output in a plain format, without any rich formatting or colors.
     * This mode is useful for environments that do not support rich output, such as some IDEs or log files.
     */
    PLAIN,
    /**
     * Renders the console output in a quiet format, suppressing most output except for errors and warnings.
     * This mode is useful for CI/CD pipelines or when you want to minimize console noise.
     */
    QUIET;
}
