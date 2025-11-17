package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JmapCLIBuilder implements CLIBuilder<Process, JmapCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jmap.exe" : "jmap";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private String processId;

    private JmapCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JmapCLIBuilder create(JDK jdk) {
        return new JmapCLIBuilder(jdk);
    }

    @Override
    public JmapCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JmapCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JmapCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JmapCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JmapCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JmapCLIBuilder processId(long pid) {
        if (pid <= 0)
            throw new IllegalArgumentException("PID must be positive");

        this.processId = Long.toString(pid);
        return this;
    }

    public JmapCLIBuilder processId(String pid) {
        Objects.requireNonNull(pid, "PID cannot be null");
        this.processId = pid;
        return this;
    }

    public JmapCLIBuilder classLoaderStats() {
        this.arguments.add("-clstats");
        return this;
    }

    public JmapCLIBuilder finalizerInfo() {
        this.arguments.add("-finalizerinfo");
        return this;
    }

    public JmapCLIBuilder histogram(boolean liveOnly) {
        this.arguments.add(liveOnly ? "-histo:live" : "-histo");
        return this;
    }

    public JmapCLIBuilder dumpHeap(boolean liveOnly, String format, Path file) {
        Objects.requireNonNull(format, "Dump format cannot be null");
        Objects.requireNonNull(file, "Dump file cannot be null");

        var options = new StringJoiner(",");
        if (liveOnly)
            options.add("live");
        options.add("format=" + format);
        options.add("file=" + file);
        this.arguments.add("-dump:" + options);
        return this;
    }

    public JmapCLIBuilder dumpHeap(String options) {
        Objects.requireNonNull(options, "Dump options cannot be null");
        this.arguments.add("-dump:" + options);
        return this;
    }

    public JmapCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    public JmapCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    @Override
    public Process run() {
        if (processId == null)
            throw new IllegalStateException("A process ID must be specified for jmap.");

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jmap");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jmap process", exception);
        }
    }
}
