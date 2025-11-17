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

    public static JpsCLIBuilder create(JDK jdk) {
        return new JpsCLIBuilder(jdk);
    }

    @Override
    public JpsCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JpsCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JpsCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JpsCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JpsCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JpsCLIBuilder quiet() {
        this.arguments.add("-q");
        return this;
    }

    public JpsCLIBuilder showMainArguments() {
        this.arguments.add("-m");
        return this;
    }

    public JpsCLIBuilder showMainClassOrJar() {
        this.arguments.add("-l");
        return this;
    }

    public JpsCLIBuilder showJvmArguments() {
        this.arguments.add("-v");
        return this;
    }

    public JpsCLIBuilder showOnlyIdentifiers() {
        this.arguments.add("-V");
        return this;
    }

    public JpsCLIBuilder host(String hostIdentifier) {
        Objects.requireNonNull(hostIdentifier, "Host identifier cannot be null");
        this.hostIdentifier = hostIdentifier;
        return this;
    }

    public JpsCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

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
