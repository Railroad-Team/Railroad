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
 * Builder that drives the {@code rmic} tool for generating RMI stubs and skeletons.
 * <p>
 * Wraps options for classpaths, destinations, protocols, and verbosity flags.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/rmic.html">rmic command documentation</a>
 */
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

    /**
     * Creates a new builder instance for the supplied {@link JDK}.
     *
     * @param jdk the JDK installation that will run {@code rmic}; must not be null
     * @return a fresh {@link RmicCLIBuilder}
     * @throws NullPointerException if the JDK is null
     */
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

    /**
     * Defines the bootstrap class path entries for the {@code rmic} command.
     *
     * @param paths the bootstrap entries; must not be null or contain null elements
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the entries array or any entry is null
     */
    public RmicCLIBuilder bootClassPath(String... paths) {
        Objects.requireNonNull(paths, "Boot class path entries cannot be null");
        for (String path : paths) {
            Objects.requireNonNull(path, "Boot class path entry cannot be null");
        }
        this.arguments.add("-bootclasspath");
        this.arguments.add(String.join(File.pathSeparator, paths));
        return this;
    }

    /**
     * Defines the bootstrap class path entries for the {@code rmic} command using {@link Path} objects.
     *
     * @param paths the bootstrap entries as {@code Path} objects; must not be null or contain nulls
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the entries array or any entry is null
     */
    public RmicCLIBuilder bootClassPath(Path... paths) {
        Objects.requireNonNull(paths, "Boot class path entries cannot be null");
        String[] entries = Arrays.stream(paths).map(Path::toString).toArray(String[]::new);
        return bootClassPath(entries);
    }

    /**
     * Sets the classpath entries for the {@code rmic} command.
     *
     * @param entries the classpath entries; must not be null or contain null values
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the array or included values are null
     */
    public RmicCLIBuilder classpath(String... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        for (String entry : entries) {
            Objects.requireNonNull(entry, "Classpath entry cannot be null");
        }
        this.arguments.add("-classpath");
        this.arguments.add(String.join(File.pathSeparator, entries));
        return this;
    }

    /**
     * Sets the classpath entries for the {@code rmic} command using {@link Path} objects.
     *
     * @param entries the classpath entries as {@code Path} values; must not be null
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the array or any element is null
     */
    public RmicCLIBuilder classpath(Path... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        String[] paths = Arrays.stream(entries).map(Path::toString).toArray(String[]::new);
        return classpath(paths);
    }

    /**
     * Sets the target directory where {@code rmic} will place generated files.
     *
     * @param directory the output directory; must not be null
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the directory is null
     */
    public RmicCLIBuilder destinationDirectory(String directory) {
        Objects.requireNonNull(directory, "Destination directory cannot be null");
        this.arguments.add("-d");
        this.arguments.add(directory);
        return this;
    }

    /**
     * Sets the target directory for generated sources using a {@link Path}.
     *
     * @param directory the output directory; must not be null
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the directory is null
     */
    public RmicCLIBuilder destinationDirectory(Path directory) {
        Objects.requireNonNull(directory, "Destination directory cannot be null");
        return destinationDirectory(directory.toString());
    }

    /**
     * Enables debug information generation via the {@code -g} flag.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder generateDebugInfo() {
        this.arguments.add("-g");
        return this;
    }

    /**
     * Adds a JVM option passed through to the underlying Java launcher.
     *
     * @param option the option to pass; must not be null
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the option is null
     */
    public RmicCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    /**
     * Instructs {@code rmic} to keep the generated source files.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder keepGeneratedSources() {
        this.arguments.add("-keepgenerated");
        return this;
    }

    /**
     * Suppresses warning output from {@code rmic}.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder noWarnings() {
        this.arguments.add("-nowarn");
        return this;
    }

    /**
     * Prevents {@code rmic} from writing files to disk.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder noWrite() {
        this.arguments.add("-nowrite");
        return this;
    }

    /**
     * Enables protocol compatibility mode with legacy RMI wire formats.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder protocolCompat() {
        this.arguments.add("-vcompat");
        return this;
    }

    /**
     * Forces the use of the RMI protocol version 1.1.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder protocolV11() {
        this.arguments.add("-v1.1");
        return this;
    }

    /**
     * Forces the use of the RMI protocol version 1.2.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder protocolV12() {
        this.arguments.add("-v1.2");
        return this;
    }

    /**
     * Enables verbose output from {@code rmic}.
     *
     * @return the current {@link RmicCLIBuilder} instance
     */
    public RmicCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    /**
     * Adds a fully qualified class name to process with {@code rmic}.
     *
     * @param className the class to process; must not be null or blank
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException     if the class name is null
     * @throws IllegalArgumentException if the class name is blank
     */
    public RmicCLIBuilder addClassName(String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        if (className.isBlank())
            throw new IllegalArgumentException("Class name cannot be blank");

        this.classNames.add(className);
        return this;
    }

    /**
     * Adds multiple class names for {@code rmic} to process.
     *
     * @param classNames the classes to process; must not be null or contain nulls
     * @return the current {@link RmicCLIBuilder} instance
     * @throws NullPointerException if the class names array or an entry is null
     */
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
