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
 * A fluent builder for constructing and executing {@code jdeprscan} commands.
 * <p>
 * This builder provides methods to configure various options for scanning JAR files
 * for usages of deprecated APIs, including specifying class paths, releases, and verbosity.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdeprscan.html">jdeprscan command documentation</a>
 */
public class JdeprscanCLIBuilder implements CLIBuilder<Process, JdeprscanCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jdeprscan.exe" : "jdeprscan";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> targets = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JdeprscanCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a new {@code JdeprscanCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code jdeprscan} command.
     * @return A new builder instance.
     */
    public static JdeprscanCLIBuilder create(JDK jdk) {
        return new JdeprscanCLIBuilder(jdk);
    }

    @Override
    public JdeprscanCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JdeprscanCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JdeprscanCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JdeprscanCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JdeprscanCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Specifies the class path for scanning. Corresponds to the {@code --class-path} option.
     *
     * @param paths The class path entries.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder classPath(String... paths) {
        Objects.requireNonNull(paths, "Class path entries cannot be null");
        this.arguments.add("--class-path " + String.join(File.pathSeparator, paths));
        return this;
    }

    /**
     * Specifies the class path for scanning. Corresponds to the {@code --class-path} option.
     *
     * @param paths The class path entries.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder classPath(Path... paths) {
        Objects.requireNonNull(paths, "Class path entries cannot be null");
        return classPath(Arrays.stream(paths).map(Path::toString).toArray(String[]::new));
    }

    /**
     * Scans for APIs that are deprecated for removal. Corresponds to the {@code --for-removal} option.
     *
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder forRemovalOnly() {
        this.arguments.add("--for-removal");
        return this;
    }

    /**
     * Displays full version information. Corresponds to the {@code --full-version} option.
     *
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder fullVersion() {
        this.arguments.add("--full-version");
        return this;
    }

    /**
     * Displays the help message. Corresponds to the {@code --help} option.
     *
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Lists all deprecated APIs. Corresponds to the {@code --list} option.
     *
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder listDeprecatedApis() {
        this.arguments.add("--list");
        return this;
    }

    /**
     * Specifies the Java SE release version to scan against. Corresponds to the {@code --release} option.
     *
     * @param release The release version.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder release(int release) {
        this.arguments.add("--release " + release);
        return this;
    }

    /**
     * Specifies the Java SE release version to scan against. Corresponds to the {@code --release} option.
     *
     * @param release The release version.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder release(String release) {
        Objects.requireNonNull(release, "Release cannot be null");
        this.arguments.add("--release " + release);
        return this;
    }

    /**
     * Enables verbose output. Corresponds to the {@code --verbose} option.
     *
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    /**
     * Displays version information. Corresponds to the {@code --version} option.
     *
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Adds a target (JAR file, class file, or directory) to scan.
     *
     * @param path The path to the target.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder addTarget(Path path) {
        Objects.requireNonNull(path, "Target path cannot be null");
        this.targets.add(path.toString());
        return this;
    }

    /**
     * Adds a target (JAR file, class file, or directory) to scan.
     *
     * @param classOrDir The class name or directory path.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder addTarget(String classOrDir) {
        Objects.requireNonNull(classOrDir, "Target cannot be null");
        this.targets.add(classOrDir);
        return this;
    }

    /**
     * Adds multiple targets (JAR files, class files, or directories) to scan.
     *
     * @param targets The targets to add.
     * @return This builder instance.
     */
    public JdeprscanCLIBuilder addTargets(String... targets) {
        Objects.requireNonNull(targets, "Targets cannot be null");
        Collections.addAll(this.targets, targets);
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        command.addAll(targets);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jdeprscan");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jdeprscan process", exception);
        }
    }
}
