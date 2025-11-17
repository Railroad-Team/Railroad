package dev.railroadide.railroad.java.cli;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Defines a generic, fluent-style builder for constructing and executing command-line processes.
 * This interface provides a common structure for setting arguments, environment variables,
 * and other execution parameters.
 *
 * @param <R> The result type returned by the {@link #run()} method (e.g., {@link Process}).
 * @param <T> The concrete type of the builder implementation, enabling method chaining.
 */
public interface CLIBuilder<R, T extends CLIBuilder<R, T>> {
    /**
     * Adds a single command-line argument to the process.
     *
     * @param arg The argument to add.
     * @return The builder instance for chaining.
     */
    T addArgument(String arg);

    /**
     * Sets the working directory for the process.
     *
     * @param path The path to the working directory.
     * @return The builder instance for chaining.
     */
    T setWorkingDirectory(Path path);

    /**
     * Sets a custom environment variable for the process.
     *
     * @param key   The name of the environment variable.
     * @param value The value of the environment variable.
     * @return The builder instance for chaining.
     */
    T setEnvironmentVariable(String key, String value);

    /**
     * Specifies whether the process should inherit the environment variables of the current process.
     *
     * @param useSystemVars {@code true} to inherit system environment variables, {@code false} otherwise.
     * @return The builder instance for chaining.
     */
    T useSystemEnvironmentVariables(boolean useSystemVars);

    /**
     * Sets a timeout for the process execution.
     *
     * @param seconds The timeout duration in seconds.
     * @return The builder instance for chaining.
     */
    default T setTimeout(long seconds) {
        return setTimeout(seconds, TimeUnit.SECONDS);
    }

    /**
     * Sets a timeout for the process execution with a specific time unit.
     *
     * @param duration The timeout duration.
     * @param unit     The {@link TimeUnit} of the duration.
     * @return The builder instance for chaining.
     */
    T setTimeout(long duration, TimeUnit unit);

    /**
     * Executes the configured command-line process.
     *
     * @return The result of the process execution, typically a {@link Process} object.
     * @throws IllegalStateException if the builder is not in a runnable state (e.g., missing required parameters).
     * @throws RuntimeException      if the process fails to start.
     */
    R run();
}
