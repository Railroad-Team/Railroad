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
 * Builder for running {@code serialver} to print serialVersionUID values.
 * <p>
 * Allows configuring classpaths, JVM options, and the list of classes to analyze.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/serialver.html">serialver command documentation</a>
 */
public class SerialverCLIBuilder implements CLIBuilder<Process, SerialverCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "serialver.exe" : "serialver";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> classNames = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private SerialverCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a {@link SerialverCLIBuilder} that will run {@code serialver} using the provided {@link JDK}.
     *
     * @param jdk the JDK installation to use; must not be null
     * @return a fresh {@link SerialverCLIBuilder}
     * @throws NullPointerException if the JDK is null
     */
    public static SerialverCLIBuilder create(JDK jdk) {
        return new SerialverCLIBuilder(jdk);
    }

    @Override
    public SerialverCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public SerialverCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public SerialverCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public SerialverCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public SerialverCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Sets the {@code -classpath} entries for {@code serialver}.
     *
     * @param entries the classpath entries; must not be null or contain null elements
     * @return the current {@link SerialverCLIBuilder} instance
     * @throws NullPointerException if the entries array or any entry is null
     */
    public SerialverCLIBuilder classpath(String... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        for (String entry : entries) {
            Objects.requireNonNull(entry, "Classpath entry cannot be null");
        }

        this.arguments.add("-classpath");
        this.arguments.add(String.join(File.pathSeparator, entries));
        return this;
    }

    /**
     * Sets the {@code -classpath} for {@code serialver} using {@link Path} values.
     *
     * @param entries the classpath entries as {@code Path}; must not be null
     * @return the current {@link SerialverCLIBuilder} instance
     * @throws NullPointerException if the array or any entry is null
     */
    public SerialverCLIBuilder classpath(Path... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        String[] paths = Arrays.stream(entries).map(Path::toString).toArray(String[]::new);
        return classpath(paths);
    }

    /**
     * Passes a JVM argument through the {@link JDK} launcher.
     *
     * @param option the JVM option to pass; must not be null
     * @return the current {@link SerialverCLIBuilder} instance
     * @throws NullPointerException if the option is null
     */
    public SerialverCLIBuilder javaOption(String option) {
        Objects.requireNonNull(option, "Java option cannot be null");
        this.arguments.add("-J" + option);
        return this;
    }

    /**
     * Adds a fully qualified class name for {@code serialver} to analyze.
     *
     * @param className the class to inspect; must not be null or blank
     * @return the current {@link SerialverCLIBuilder} instance
     * @throws NullPointerException     if the class name is null
     * @throws IllegalArgumentException if the class name is blank
     */
    public SerialverCLIBuilder addClassName(String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        if (className.isBlank())
            throw new IllegalArgumentException("Class name cannot be blank");

        this.classNames.add(className);
        return this;
    }

    /**
     * Adds multiple class names to analyze in {@code serialver}.
     *
     * @param classNames the classes to inspect; must not be null or contain null elements
     * @return the current {@link SerialverCLIBuilder} instance
     * @throws NullPointerException if the class names array or an entry is null
     */
    public SerialverCLIBuilder addClassNames(String... classNames) {
        Objects.requireNonNull(classNames, "Class names cannot be null");
        for (String className : classNames) {
            addClassName(className);
        }

        return this;
    }

    @Override
    public Process run() {
        if (classNames.isEmpty())
            throw new IllegalStateException("At least one class name must be specified for serialver.");

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "serialver");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start serialver process", exception);
        }
    }
}
