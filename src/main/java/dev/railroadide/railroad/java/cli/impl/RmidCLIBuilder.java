package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class RmidCLIBuilder implements CLIBuilder<Process, RmidCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "rmid.exe" : "rmid";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private RmidCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static RmidCLIBuilder create(JDK jdk) {
        return new RmidCLIBuilder(jdk);
    }

    @Override
    public RmidCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public RmidCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public RmidCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public RmidCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public RmidCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public RmidCLIBuilder childProcessOption(String option) {
        Objects.requireNonNull(option, "Child process option cannot be null");
        this.arguments.add("-C" + option);
        return this;
    }

    public RmidCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    public RmidCLIBuilder execPolicy(String policy) {
        Objects.requireNonNull(policy, "Execution policy cannot be null");
        if (policy.isBlank())
            throw new IllegalArgumentException("Execution policy cannot be blank");

        this.arguments.add("-J-Dsun.rmi.activation.execPolicy=" + policy);
        return this;
    }

    public RmidCLIBuilder logDirectory(String directory) {
        Objects.requireNonNull(directory, "Log directory cannot be null");
        this.arguments.add("-log");
        this.arguments.add(directory);
        return this;
    }

    public RmidCLIBuilder logDirectory(Path directory) {
        Objects.requireNonNull(directory, "Log directory cannot be null");
        return logDirectory(directory.toString());
    }

    public RmidCLIBuilder port(int port) {
        if (port <= 0 || port > 65535)
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        this.arguments.add("-port");
        this.arguments.add(Integer.toString(port));
        return this;
    }

    public RmidCLIBuilder stop() {
        this.arguments.add("-stop");
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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "rmid");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start rmid process", exception);
        }
    }
}
