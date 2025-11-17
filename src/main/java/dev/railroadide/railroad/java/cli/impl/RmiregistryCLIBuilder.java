package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Fluent builder for starting {@code rmiregistry} processes via a given {@link JDK}.
 * <p>
 * Supports setting ports and JVM options before launching the registry.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/rmiregistry.html">rmiregistry command documentation</a>
 */
public class RmiregistryCLIBuilder implements CLIBuilder<Process, RmiregistryCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "rmiregistry.exe" : "rmiregistry";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private Integer port;

    private RmiregistryCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a new {@link RmiregistryCLIBuilder} that will launch {@code rmiregistry} using the supplied {@link JDK}.
     *
     * @param jdk the JDK installation to use; must not be null
     * @return a fresh {@link RmiregistryCLIBuilder}
     * @throws NullPointerException if the JDK is null
     */
    public static RmiregistryCLIBuilder create(JDK jdk) {
        return new RmiregistryCLIBuilder(jdk);
    }

    @Override
    public RmiregistryCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public RmiregistryCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public RmiregistryCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public RmiregistryCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public RmiregistryCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Passes a JVM argument through the {@link JDK} launcher invoked by {@code rmiregistry}.
     *
     * @param option the JVM option to pass; must not be null
     * @return the current {@link RmiregistryCLIBuilder} instance
     * @throws NullPointerException if the option is null
     */
    public RmiregistryCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    /**
     * Sets the port number {@code rmiregistry} should bind to.
     *
     * @param port the port value between 1 and 65535
     * @return the current {@link RmiregistryCLIBuilder} instance
     * @throws IllegalArgumentException if the port is outside the valid range
     */
    public RmiregistryCLIBuilder port(int port) {
        if (port <= 0 || port > 65535)
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        this.port = port;
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        if (port != null)
            command.add(Integer.toString(port));

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "rmiregistry");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start rmiregistry process", exception);
        }
    }
}
