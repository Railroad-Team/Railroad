package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.vcs.git.util.GitRepository;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Immutable description of a git command invocation.
 */
public final class GitCommand {
    private final List<String> arguments;
    private final Path workingDirectory;
    private final long timeoutMs;
    private final Map<String, String> environment;
    private final boolean streamStdoutToListener;

    private GitCommand(
        List<String> arguments,
        Path workingDirectory,
        long timeoutMs,
        Map<String, String> environment,
        boolean streamStdoutToListener
    ) {
        this.arguments = List.copyOf(arguments);
        this.workingDirectory = workingDirectory;
        this.timeoutMs = timeoutMs;
        this.environment = Map.copyOf(environment);
        this.streamStdoutToListener = streamStdoutToListener;
    }

    /**
     * Gets the argument list passed to the git executable.
     *
     * @return immutable command arguments in invocation order
     */
    public List<String> arguments() {
        return arguments;
    }

    /**
     * Gets the command working directory.
     *
     * @return working directory path, or {@code null} to use the process default
     */
    public Path workingDirectory() {
        return workingDirectory;
    }

    /**
     * Gets the configured timeout.
     *
     * @return timeout in milliseconds, or {@code 0} for no explicit timeout
     */
    public long timeoutMillis() {
        return timeoutMs;
    }

    /**
     * Gets environment overrides for the git process.
     *
     * @return immutable map of environment variables
     */
    public Map<String, String> environment() {
        return environment;
    }

    /**
     * Indicates whether stdout should be streamed to the output listener.
     *
     * @return {@code true} when stdout streaming is enabled
     */
    public boolean streamStdoutToListener() {
        return streamStdoutToListener;
    }

    /**
     * Creates a builder for constructing a git command.
     *
     * @return new mutable {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Renders command arguments as a single space-delimited string.
     *
     * @return joined argument string for logging/debug output
     */
    public String argsString() {
        return String.join(" ", arguments);
    }

    /**
     * Fluent builder for constructing {@link GitCommand} instances.
     */
    public static class Builder {
        private final List<String> arguments = new ArrayList<>();
        private Path workingDirectory = null;
        private long timeoutMs = 0;
        private Map<String, String> environment = new HashMap<>();
        private boolean streamStdoutToListener = false;

        /**
         * Appends string arguments to the command.
         *
         * @param args arguments to append
         * @return this builder
         */
        public Builder addArgs(String... args) {
            this.arguments.addAll(Arrays.asList(args));
            return this;
        }

        /**
         * Appends arguments converted with {@link String#valueOf(Object)}.
         *
         * @param args arguments to append
         * @return this builder
         */
        public Builder addArgs(Object... args) {
            for (Object arg : args) {
                this.arguments.add(String.valueOf(arg));
            }

            return this;
        }

        /**
         * Sets the process working directory.
         *
         * @param path working directory path
         * @return this builder
         */
        public Builder workingDirectory(Path path) {
            this.workingDirectory = path;
            return this;
        }

        /**
         * Sets the working directory to a repository root path.
         *
         * @param repository repository whose root is used as working directory
         * @return this builder
         */
        public Builder workingDirectory(GitRepository repository) {
            this.workingDirectory = repository.root();
            return this;
        }

        /**
         * Sets the command timeout.
         *
         * @param duration timeout duration value
         * @param unit timeout duration unit
         * @return this builder
         */
        public Builder timeout(long duration, TimeUnit unit) {
            this.timeoutMs = unit.toMillis(duration);
            return this;
        }

        /**
         * Replaces process environment overrides.
         *
         * @param env environment variable map
         * @return this builder
         */
        public Builder environment(Map<String, String> env) {
            this.environment = env;
            return this;
        }

        /**
         * Enables or disables stdout streaming.
         *
         * @param stream whether stdout should be forwarded to the listener
         * @return this builder
         */
        public Builder streamStdoutToListener(boolean stream) {
            this.streamStdoutToListener = stream;
            return this;
        }

        /**
         * Builds an immutable command instance.
         *
         * @return constructed {@link GitCommand}
         */
        public GitCommand build() {
            return new GitCommand(
                this.arguments,
                this.workingDirectory,
                this.timeoutMs,
                this.environment,
                this.streamStdoutToListener
            );
        }
    }
}
