package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JpsCLIBuilder implements CLIBuilder<Process, JpsCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jps.exe" : "jps";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private String hostIdentifier;

    private JpsCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a new builder instance tied to the provided {@link JDK}.
     *
     * @param jdk the JDK to use
     * @return builder ready for configuration
     */
    public static JpsCLIBuilder create(JDK jdk) {
        return new JpsCLIBuilder(jdk);
    }

    /**
     * Adds a raw CLI argument to {@code jps}.
     *
     * @param arg argument value
     * @return this builder
     */
    @Override
    public JpsCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    /**
     * Sets the working directory for the {@code jps} process.
     *
     * @param path working directory
     * @return this builder
     */
    @Override
    public JpsCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    /**
     * Adds or overrides an environment variable for the spawned process.
     *
     * @param key   variable name
     * @param value variable value
     * @return this builder
     */
    @Override
    public JpsCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    /**
     * Controls whether the new process inherits the system environment variables.
     *
     * @param useSystemVars whether to inherit system variables
     * @return this builder
     */
    @Override
    public JpsCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    /**
     * Sets a timeout applied when enforcing process termination.
     *
     * @param duration timeout duration
     * @param unit     time unit
     * @return this builder
     */
    @Override
    public JpsCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Adds the {@code -q} flag to suppress headers.
     *
     * @return this builder
     */
    public JpsCLIBuilder quiet() {
        this.arguments.add("-q");
        return this;
    }

    /**
     * Adds {@code -m} to show main arguments for each JVM.
     *
     * @return this builder
     */
    public JpsCLIBuilder showMainArguments() {
        this.arguments.add("-m");
        return this;
    }

    /**
     * Adds {@code -l} to show the main class or JAR for each JVM.
     *
     * @return this builder
     */
    public JpsCLIBuilder showMainClassOrJar() {
        this.arguments.add("-l");
        return this;
    }

    /**
     * Adds {@code -v} to include JVM arguments in the output.
     *
     * @return this builder
     */
    public JpsCLIBuilder showJvmArguments() {
        this.arguments.add("-v");
        return this;
    }

    /**
     * Adds {@code -V} to show only JVM identifiers.
     *
     * @return this builder
     */
    public JpsCLIBuilder showOnlyIdentifiers() {
        this.arguments.add("-V");
        return this;
    }

    /**
     * Targets a remote host identifier.
     *
     * @param hostIdentifier host address or name
     * @return this builder
     */
    public JpsCLIBuilder host(String hostIdentifier) {
        Objects.requireNonNull(hostIdentifier, "Host identifier cannot be null");
        this.hostIdentifier = hostIdentifier;
        return this;
    }

    /**
     * Adds {@code -help} to the argument list.
     *
     * @return this builder
     */
    public JpsCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    /**
     * Starts the configured {@code jps} process enforcing the timeout policy.
     *
     * @return started process
     */
    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        if (hostIdentifier != null)
            command.add(hostIdentifier);

        var processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        if (useSystemEnvVars) {
            Map<String, String> env = processBuilder.environment();
            env.putAll(environmentVariables);
        } else {
            processBuilder.environment().clear();
            processBuilder.environment().putAll(environmentVariables);
        }

        try {
            Process process = processBuilder.start();
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jps");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jps process", exception);
        }
    }
}
