package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Builder for configuring {@code jinfo} invocations against a specific {@link JDK}.
 * <p>
 * Supports toggling JVM flags, printing system properties, and targeting particular JVM processes.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jinfo.html">jinfo command documentation</a>
 */
public class JinfoCLIBuilder implements CLIBuilder<Process, JinfoCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jinfo.exe" : "jinfo";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private String processId;

    private JinfoCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JinfoCLIBuilder create(JDK jdk) {
        return new JinfoCLIBuilder(jdk);
    }

    @Override
    public JinfoCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JinfoCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JinfoCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JinfoCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JinfoCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Sets the process ID for the `jinfo` command using a long value.
     *
     * @param pid the process ID; must be positive
     * @return the current `JinfoCLIBuilder` instance
     * @throws IllegalArgumentException if the PID is not positive
     */
    public JinfoCLIBuilder processId(long pid) {
        if (pid <= 0)
            throw new IllegalArgumentException("PID must be positive");

        this.processId = Long.toString(pid);
        return this;
    }

    /**
     * Sets the process ID for the `jinfo` command using a string value.
     *
     * @param pid the process ID as a string; must not be null
     * @return the current `JinfoCLIBuilder` instance
     * @throws NullPointerException if the PID is null
     */
    public JinfoCLIBuilder processId(String pid) {
        Objects.requireNonNull(pid, "PID cannot be null");
        this.processId = pid;
        return this;
    }

    /**
     * Adds a flag to the `jinfo` command.
     *
     * @param name the name of the flag; must not be null
     * @return the current `JinfoCLIBuilder` instance
     * @throws NullPointerException if the flag name is null
     */
    public JinfoCLIBuilder flag(String name) {
        Objects.requireNonNull(name, "Flag name cannot be null");
        this.arguments.add("-flag " + name);
        return this;
    }

    /**
     * Adds a flag to the `jinfo` command with an enabled or disabled state.
     *
     * @param name    the name of the flag; must not be null
     * @param enabled whether the flag is enabled (true) or disabled (false)
     * @return the current `JinfoCLIBuilder` instance
     * @throws NullPointerException if the flag name is null
     */
    public JinfoCLIBuilder flag(String name, boolean enabled) {
        Objects.requireNonNull(name, "Flag name cannot be null");
        this.arguments.add("-flag " + (enabled ? "+" : "-") + name);
        return this;
    }

    /**
     * Adds a flag to the `jinfo` command with a specific value.
     *
     * @param name  the name of the flag; must not be null
     * @param value the value of the flag; must not be null
     * @return the current `JinfoCLIBuilder` instance
     * @throws NullPointerException if the flag name or value is null
     */
    public JinfoCLIBuilder flag(String name, String value) {
        Objects.requireNonNull(name, "Flag name cannot be null");
        Objects.requireNonNull(value, "Flag value cannot be null");
        this.arguments.add("-flag " + name + "=" + value);
        return this;
    }

    /**
     * Adds the `-flags` option to the `jinfo` command to print all flags.
     *
     * @return the current `JinfoCLIBuilder` instance
     */
    public JinfoCLIBuilder printFlags() {
        this.arguments.add("-flags");
        return this;
    }

    /**
     * Adds the `-sysprops` option to the `jinfo` command to print all system properties.
     *
     * @return the current `JinfoCLIBuilder` instance
     */
    public JinfoCLIBuilder printSystemProperties() {
        this.arguments.add("-sysprops");
        return this;
    }

    /**
     * Adds the `-help` option to the `jinfo` command to display help information.
     *
     * @return the current `JinfoCLIBuilder` instance
     */
    public JinfoCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    /**
     * Adds a Java option to the `jinfo` command.
     *
     * @param option the Java option; must not be null
     * @return the current `JinfoCLIBuilder` instance
     * @throws NullPointerException if the Java option is null
     */
    public JinfoCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    @Override
    public Process run() {
        if (processId == null)
            throw new IllegalStateException("A process ID must be specified for jinfo.");

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        command.add(processId);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jinfo");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jinfo process", exception);
        }
    }
}
