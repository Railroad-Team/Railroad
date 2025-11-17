package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JdbCLIBuilder implements CLIBuilder<Process, JdbCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jdb.exe" : "jdb";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> targetArguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private String mainClass;

    private JdbCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JdbCLIBuilder create(JDK jdk) {
        return new JdbCLIBuilder(jdk);
    }

    @Override
    public JdbCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JdbCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JdbCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JdbCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JdbCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JdbCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    public JdbCLIBuilder sourcePath(String... directories) {
        Objects.requireNonNull(directories, "Source directories cannot be null");
        this.arguments.add("-sourcepath " + String.join(File.pathSeparator, directories));
        return this;
    }

    public JdbCLIBuilder attach(String address) {
        Objects.requireNonNull(address, "Attach address cannot be null");
        this.arguments.add("-attach " + address);
        return this;
    }

    public JdbCLIBuilder listen(String address) {
        Objects.requireNonNull(address, "Listen address cannot be null");
        this.arguments.add("-listen " + address);
        return this;
    }

    public JdbCLIBuilder listenAny() {
        this.arguments.add("-listenany");
        return this;
    }

    public JdbCLIBuilder launchOnStart() {
        this.arguments.add("-launch");
        return this;
    }

    public JdbCLIBuilder listConnectors() {
        this.arguments.add("-listconnectors");
        return this;
    }

    public JdbCLIBuilder connect(String connectorName, Map<String, String> arguments) {
        Objects.requireNonNull(connectorName, "Connector name cannot be null");
        Objects.requireNonNull(arguments, "Connector arguments cannot be null");
        var builder = new StringBuilder("-connect ").append(connectorName);
        arguments.forEach((key, value) -> builder.append(":").append(key).append("=").append(value));
        this.arguments.add(builder.toString());
        return this;
    }

    public JdbCLIBuilder debugTrace(String flags) {
        Objects.requireNonNull(flags, "Debug trace flags cannot be null");
        this.arguments.add("-dbgtrace " + flags);
        return this;
    }

    public JdbCLIBuilder tClient() {
        this.arguments.add("-tclient");
        return this;
    }

    public JdbCLIBuilder trackAllThreads() {
        this.arguments.add("-trackallthreads");
        return this;
    }

    public JdbCLIBuilder tServer() {
        this.arguments.add("-tserver");
        return this;
    }

    public JdbCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    public JdbCLIBuilder debuggeeOption(String option) {
        Objects.requireNonNull(option, "Debuggee option cannot be null");
        this.arguments.add("-R" + option);
        return this;
    }

    public JdbCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    public JdbCLIBuilder verbose(String mode) {
        Objects.requireNonNull(mode, "Verbose mode cannot be null");
        this.arguments.add("-verbose:" + mode);
        return this;
    }

    public JdbCLIBuilder systemProperty(String key, String value) {
        Objects.requireNonNull(key, "Property key cannot be null");
        Objects.requireNonNull(value, "Property value cannot be null");
        this.arguments.add("-D" + key + "=" + value);
        return this;
    }

    public JdbCLIBuilder classpath(String... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        this.arguments.add("-classpath " + String.join(File.pathSeparator, entries));
        return this;
    }

    public JdbCLIBuilder xOption(String option) {
        Objects.requireNonNull(option, "Nonstandard option cannot be null");
        this.arguments.add("-X" + option);
        return this;
    }

    public JdbCLIBuilder mainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "Main class cannot be null");
        this.mainClass = mainClass;
        return this;
    }

    public JdbCLIBuilder mainClassArguments(String... args) {
        Objects.requireNonNull(args, "Main class arguments cannot be null");
        Collections.addAll(this.targetArguments, args);
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        if (mainClass != null) {
            command.add(mainClass);
        }
        command.addAll(targetArguments);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jdb");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jdb process", exception);
        }
    }
}
