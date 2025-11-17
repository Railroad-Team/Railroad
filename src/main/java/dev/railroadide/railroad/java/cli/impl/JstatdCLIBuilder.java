package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JstatdCLIBuilder implements CLIBuilder<Process, JstatdCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jstatd.exe" : "jstatd";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JstatdCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JstatdCLIBuilder create(JDK jdk) {
        return new JstatdCLIBuilder(jdk);
    }

    @Override
    public JstatdCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JstatdCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JstatdCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JstatdCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JstatdCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JstatdCLIBuilder noInternalRegistry() {
        this.arguments.add("-nr");
        return this;
    }

    public JstatdCLIBuilder registryPort(int port) {
        validatePort(port, "Registry port must be between 1 and 65535");
        this.arguments.add("-p");
        this.arguments.add(Integer.toString(port));
        return this;
    }

    public JstatdCLIBuilder connectorPort(int port) {
        validatePort(port, "Connector port must be between 1 and 65535");
        this.arguments.add("-r");
        this.arguments.add(Integer.toString(port));
        return this;
    }

    public JstatdCLIBuilder rmiObjectName(String name) {
        Objects.requireNonNull(name, "RMI object name cannot be null");
        if (name.isBlank())
            throw new IllegalArgumentException("RMI object name cannot be blank");

        this.arguments.add("-n");
        this.arguments.add(name);
        return this;
    }

    public JstatdCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jstatd");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jstatd process", exception);
        }
    }

    private static void validatePort(int port, String message) {
        if (port <= 0 || port > 65535)
            throw new IllegalArgumentException(message);
    }
}
