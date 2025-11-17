package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class RmicCLIBuilder implements CLIBuilder<Process, RmicCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "rmic.exe" : "rmic";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> classNames = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private RmicCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static RmicCLIBuilder create(JDK jdk) {
        return new RmicCLIBuilder(jdk);
    }

    @Override
    public RmicCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public RmicCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public RmicCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public RmicCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public RmicCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public RmicCLIBuilder bootClassPath(String... paths) {
        Objects.requireNonNull(paths, "Boot class path entries cannot be null");
        for (String path : paths) {
            Objects.requireNonNull(path, "Boot class path entry cannot be null");
        }
        this.arguments.add("-bootclasspath");
        this.arguments.add(String.join(File.pathSeparator, paths));
        return this;
    }

    public RmicCLIBuilder bootClassPath(Path... paths) {
        Objects.requireNonNull(paths, "Boot class path entries cannot be null");
        String[] entries = Arrays.stream(paths).map(Path::toString).toArray(String[]::new);
        return bootClassPath(entries);
    }

    public RmicCLIBuilder classpath(String... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        for (String entry : entries) {
            Objects.requireNonNull(entry, "Classpath entry cannot be null");
        }
        this.arguments.add("-classpath");
        this.arguments.add(String.join(File.pathSeparator, entries));
        return this;
    }

    public RmicCLIBuilder classpath(Path... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        String[] paths = Arrays.stream(entries).map(Path::toString).toArray(String[]::new);
        return classpath(paths);
    }

    public RmicCLIBuilder destinationDirectory(String directory) {
        Objects.requireNonNull(directory, "Destination directory cannot be null");
        this.arguments.add("-d");
        this.arguments.add(directory);
        return this;
    }

    public RmicCLIBuilder destinationDirectory(Path directory) {
        Objects.requireNonNull(directory, "Destination directory cannot be null");
        return destinationDirectory(directory.toString());
    }

    public RmicCLIBuilder generateDebugInfo() {
        this.arguments.add("-g");
        return this;
    }

    public RmicCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    public RmicCLIBuilder keepGeneratedSources() {
        this.arguments.add("-keepgenerated");
        return this;
    }

    public RmicCLIBuilder noWarnings() {
        this.arguments.add("-nowarn");
        return this;
    }

    public RmicCLIBuilder noWrite() {
        this.arguments.add("-nowrite");
        return this;
    }

    public RmicCLIBuilder protocolCompat() {
        this.arguments.add("-vcompat");
        return this;
    }

    public RmicCLIBuilder protocolV11() {
        this.arguments.add("-v1.1");
        return this;
    }

    public RmicCLIBuilder protocolV12() {
        this.arguments.add("-v1.2");
        return this;
    }

    public RmicCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    public RmicCLIBuilder addClassName(String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        if (className.isBlank())
            throw new IllegalArgumentException("Class name cannot be blank");

        this.classNames.add(className);
        return this;
    }

    public RmicCLIBuilder addClassNames(String... classNames) {
        Objects.requireNonNull(classNames, "Class names cannot be null");
        for (String className : classNames) {
            addClassName(className);
        }

        return this;
    }

    @Override
    public Process run() {
        if (classNames.isEmpty())
            throw new IllegalStateException("At least one class name must be specified for rmic.");

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        command.addAll(classNames);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "rmic");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start rmic process", exception);
        }
    }
}
