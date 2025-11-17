package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A fluent builder for constructing and executing {@code jdb} commands.
 * <p>
 * This builder provides methods to configure various options for debugging Java applications,
 * including source paths, connection details, and JVM options.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdb.html">jdb command documentation</a>
 */
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

    /**
     * Creates a new {@code JdbCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code jdb} command.
     * @return A new builder instance.
     */
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

    /**
     * Displays the help message. Corresponds to the {@code -help} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder help() {
        this.arguments.add("-help");
        return this;
    }

    /**
     * Specifies the source code path. Corresponds to the {@code -sourcepath} option.
     *
     * @param directories The directories for the source path.
     * @return This builder instance.
     */
    public JdbCLIBuilder sourcePath(String... directories) {
        Objects.requireNonNull(directories, "Source directories cannot be null");
        this.arguments.add("-sourcepath " + String.join(File.pathSeparator, directories));
        return this;
    }

    /**
     * Attaches the debugger to a running JVM. Corresponds to the {@code -attach} option.
     *
     * @param address The address of the target JVM.
     * @return This builder instance.
     */
    public JdbCLIBuilder attach(String address) {
        Objects.requireNonNull(address, "Attach address cannot be null");
        this.arguments.add("-attach " + address);
        return this;
    }

    /**
     * Listens for a debugger connection at the specified address. Corresponds to the {@code -listen} option.
     *
     * @param address The address to listen on.
     * @return This builder instance.
     */
    public JdbCLIBuilder listen(String address) {
        Objects.requireNonNull(address, "Listen address cannot be null");
        this.arguments.add("-listen " + address);
        return this;
    }

    /**
     * Listens for a debugger connection on any available address. Corresponds to the {@code -listenany} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder listenAny() {
        this.arguments.add("-listenany");
        return this;
    }

    /**
     * Launches the debuggee VM on startup. Corresponds to the {@code -launch} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder launchOnStart() {
        this.arguments.add("-launch");
        return this;
    }

    /**
     * Lists available connectors. Corresponds to the {@code -listconnectors} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder listConnectors() {
        this.arguments.add("-listconnectors");
        return this;
    }

    /**
     * Connects to a running JVM using the specified connector. Corresponds to the {@code -connect} option.
     *
     * @param connectorName The name of the connector.
     * @param arguments     A map of arguments for the connector.
     * @return This builder instance.
     */
    public JdbCLIBuilder connect(String connectorName, Map<String, String> arguments) {
        Objects.requireNonNull(connectorName, "Connector name cannot be null");
        Objects.requireNonNull(arguments, "Connector arguments cannot be null");
        var builder = new StringBuilder("-connect ").append(connectorName);
        arguments.forEach((key, value) -> builder.append(":").append(key).append("=").append(value));
        this.arguments.add(builder.toString());
        return this;
    }

    /**
     * Sets the debug trace flags. Corresponds to the {@code -dbgtrace} option.
     *
     * @param flags The debug trace flags.
     * @return This builder instance.
     */
    public JdbCLIBuilder debugTrace(String flags) {
        Objects.requireNonNull(flags, "Debug trace flags cannot be null");
        this.arguments.add("-dbgtrace " + flags);
        return this;
    }

    /**
     * Uses the client VM. Corresponds to the {@code -tclient} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder tClient() {
        this.arguments.add("-tclient");
        return this;
    }

    /**
     * Tracks all threads. Corresponds to the {@code -trackallthreads} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder trackAllThreads() {
        this.arguments.add("-trackallthreads");
        return this;
    }

    /**
     * Uses the server VM. Corresponds to the {@code -tserver} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder tServer() {
        this.arguments.add("-tserver");
        return this;
    }

    /**
     * Passes an option to the Java Virtual Machine. Corresponds to the {@code -J} option.
     *
     * @param option The option to pass to the JVM.
     * @return This builder instance.
     */
    public JdbCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    /**
     * Passes an option to the debuggee JVM. Corresponds to the {@code -R} option.
     *
     * @param option The option to pass to the debuggee JVM.
     * @return This builder instance.
     */
    public JdbCLIBuilder debuggeeOption(String option) {
        Objects.requireNonNull(option, "Debuggee option cannot be null");
        this.arguments.add("-R" + option);
        return this;
    }

    /**
     * Enables verbose output. Corresponds to the {@code -verbose} option.
     *
     * @return This builder instance.
     */
    public JdbCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    /**
     * Enables verbose output for a specific mode. Corresponds to the {@code -verbose:mode} option.
     *
     * @param mode The verbose mode.
     * @return This builder instance.
     */
    public JdbCLIBuilder verbose(String mode) {
        Objects.requireNonNull(mode, "Verbose mode cannot be null");
        this.arguments.add("-verbose:" + mode);
        return this;
    }

    /**
     * Sets a system property for the debuggee JVM. Corresponds to the {@code -Dkey=value} option.
     *
     * @param key   The property key.
     * @param value The property value.
     * @return This builder instance.
     */
    public JdbCLIBuilder systemProperty(String key, String value) {
        Objects.requireNonNull(key, "Property key cannot be null");
        Objects.requireNonNull(value, "Property value cannot be null");
        this.arguments.add("-D" + key + "=" + value);
        return this;
    }

    /**
     * Specifies the classpath for the debuggee JVM. Corresponds to the {@code -classpath} option.
     *
     * @param entries The classpath entries.
     * @return This builder instance.
     */
    public JdbCLIBuilder classpath(String... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        this.arguments.add("-classpath " + String.join(File.pathSeparator, entries));
        return this;
    }

    /**
     * Passes a non-standard option to the debuggee JVM. Corresponds to the {@code -X} option.
     *
     * @param option The non-standard option.
     * @return This builder instance.
     */
    public JdbCLIBuilder xOption(String option) {
        Objects.requireNonNull(option, "Nonstandard option cannot be null");
        this.arguments.add("-X" + option);
        return this;
    }

    /**
     * Sets the main class to be debugged.
     *
     * @param mainClass The main class name.
     * @return This builder instance.
     */
    public JdbCLIBuilder mainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "Main class cannot be null");
        this.mainClass = mainClass;
        return this;
    }

    /**
     * Adds arguments to be passed to the main class.
     *
     * @param args The arguments for the main class.
     * @return This builder instance.
     */
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
