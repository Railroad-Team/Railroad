package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Builder that wraps the {@code jmod} tool to create, inspect, and manipulate JMOD files.
 * <p>
 * Offers helpers for different operation modes, routing of paths, and advanced options exposed by {@code jmod}.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jmod.html">jmod command documentation</a>
 */
public class JmodCLIBuilder implements CLIBuilder<Process, JmodCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jmod.exe" : "jmod";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private OperationMode operationMode;
    private String jmodFile;

    private JmodCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JmodCLIBuilder create(JDK jdk) {
        return new JmodCLIBuilder(jdk);
    }

    @Override
    public JmodCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JmodCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JmodCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JmodCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JmodCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Sets the operation mode to "create" and specifies the JMOD file to create.
     *
     * @param jmodFile the JMOD file to create; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the JMOD file is null
     */
    public JmodCLIBuilder create(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.CREATE;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    /**
     * Sets the operation mode to "extract" and specifies the JMOD file to extract.
     *
     * @param jmodFile the JMOD file to extract; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the JMOD file is null
     */
    public JmodCLIBuilder extract(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.EXTRACT;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    /**
     * Sets the operation mode to "list" and specifies the JMOD file to list.
     *
     * @param jmodFile the JMOD file to list; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the JMOD file is null
     */
    public JmodCLIBuilder list(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.LIST;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    /**
     * Sets the operation mode to "describe" and specifies the JMOD file to describe.
     *
     * @param jmodFile the JMOD file to describe; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the JMOD file is null
     */
    public JmodCLIBuilder describe(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.DESCRIBE;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    /**
     * Sets the operation mode to "hash" and specifies the JMOD file to hash.
     *
     * @param jmodFile the JMOD file to hash; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the JMOD file is null
     */
    public JmodCLIBuilder hash(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.HASH;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    /**
     * Adds the `--class-path` option to the JMOD command with the specified class path.
     *
     * @param path the class path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the class path is null
     */
    public JmodCLIBuilder classPath(String path) {
        Objects.requireNonNull(path, "Class path cannot be null");
        this.arguments.add("--class-path " + path);
        return this;
    }

    /**
     * Adds the `--cmds` option to the JMOD command with the specified command path.
     *
     * @param path the command path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the command path is null
     */
    public JmodCLIBuilder cmds(Path path) {
        Objects.requireNonNull(path, "Command path cannot be null");
        this.arguments.add("--cmds " + path);
        return this;
    }

    /**
     * Adds the `--compress` option to the JMOD command with the specified compression level.
     *
     * @param compression the compression level to set; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the compression level is null
     */
    public JmodCLIBuilder compression(String compression) {
        Objects.requireNonNull(compression, "Compression cannot be null");
        this.arguments.add("--compress " + compression);
        return this;
    }

    /**
     * Adds the `--config` option to the JMOD command with the specified configuration path.
     *
     * @param path the configuration path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the configuration path is null
     */
    public JmodCLIBuilder config(Path path) {
        Objects.requireNonNull(path, "Config path cannot be null");
        this.arguments.add("--config " + path);
        return this;
    }

    /**
     * Adds the `--date` option to the JMOD command with the specified entry timestamp.
     *
     * @param timestamp the entry timestamp to set; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the timestamp is null
     */
    public JmodCLIBuilder entryTimestamp(String timestamp) {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        this.arguments.add("--date " + timestamp);
        return this;
    }

    /**
     * Adds the `--dir` option to the JMOD command with the specified extraction directory.
     *
     * @param path the extraction directory to set; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the extraction directory is null
     */
    public JmodCLIBuilder extractionDirectory(Path path) {
        Objects.requireNonNull(path, "Extraction directory cannot be null");
        this.arguments.add("--dir " + path);
        return this;
    }

    /**
     * Adds the `--dry-run` option to the JMOD command.
     *
     * @return the current `JmodCLIBuilder` instance
     */
    public JmodCLIBuilder dryRun() {
        this.arguments.add("--dry-run");
        return this;
    }

    /**
     * Adds the `--exclude` option to the JMOD command with the specified pattern list.
     *
     * @param patternList the pattern list to exclude; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the pattern list is null
     */
    public JmodCLIBuilder exclude(String patternList) {
        Objects.requireNonNull(patternList, "Pattern list cannot be null");
        this.arguments.add("--exclude " + patternList);
        return this;
    }

    /**
     * Adds the `--hash-modules` option to the JMOD command with the specified regex pattern.
     *
     * @param regexPattern the regex pattern to hash modules; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the regex pattern is null
     */
    public JmodCLIBuilder hashModules(String regexPattern) {
        Objects.requireNonNull(regexPattern, "Regex pattern cannot be null");
        this.arguments.add("--hash-modules " + regexPattern);
        return this;
    }

    /**
     * Adds the `--header-files` option to the JMOD command with the specified header files path.
     *
     * @param path the header files path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the header files path is null
     */
    public JmodCLIBuilder headerFiles(Path path) {
        Objects.requireNonNull(path, "Header files path cannot be null");
        this.arguments.add("--header-files " + path);
        return this;
    }

    /**
     * Adds the `--help` option to the JMOD command.
     *
     * @return the current `JmodCLIBuilder` instance
     */
    public JmodCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Adds the `--help-extra` option to the JMOD command.
     *
     * @return the current `JmodCLIBuilder` instance
     */
    public JmodCLIBuilder helpExtra() {
        this.arguments.add("--help-extra");
        return this;
    }

    /**
     * Adds the `--legal-notices` option to the JMOD command with the specified legal notices path.
     *
     * @param path the legal notices path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the legal notices path is null
     */
    public JmodCLIBuilder legalNotices(Path path) {
        Objects.requireNonNull(path, "Legal notices path cannot be null");
        this.arguments.add("--legal-notices " + path);
        return this;
    }

    /**
     * Adds the `--libs` option to the JMOD command with the specified libraries path.
     *
     * @param path the libraries path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the libraries path is null
     */
    public JmodCLIBuilder libs(Path path) {
        Objects.requireNonNull(path, "Libraries path cannot be null");
        this.arguments.add("--libs " + path);
        return this;
    }

    /**
     * Adds the `--main-class` option to the JMOD command with the specified main class.
     *
     * @param className the main class to set; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the main class is null
     */
    public JmodCLIBuilder mainClass(String className) {
        Objects.requireNonNull(className, "Main class cannot be null");
        this.arguments.add("--main-class " + className);
        return this;
    }

    /**
     * Adds the `--man-pages` option to the JMOD command with the specified man pages path.
     *
     * @param path the man pages path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the man pages path is null
     */
    public JmodCLIBuilder manPages(Path path) {
        Objects.requireNonNull(path, "Man pages path cannot be null");
        this.arguments.add("--man-pages " + path);
        return this;
    }

    /**
     * Adds the `--module-version` option to the JMOD command with the specified module version.
     *
     * @param version the module version to set; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the module version is null
     */
    public JmodCLIBuilder moduleVersion(String version) {
        Objects.requireNonNull(version, "Module version cannot be null");
        this.arguments.add("--module-version " + version);
        return this;
    }

    /**
     * Adds the `--module-path` option to the JMOD command with the specified module path.
     *
     * @param path the module path to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the module path is null
     */
    public JmodCLIBuilder modulePath(String path) {
        Objects.requireNonNull(path, "Module path cannot be null");
        this.arguments.add("--module-path " + path);
        return this;
    }

    /**
     * Adds the `--target-platform` option to the JMOD command with the specified platform.
     *
     * @param platform the target platform to set; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the platform is null
     */
    public JmodCLIBuilder targetPlatform(String platform) {
        Objects.requireNonNull(platform, "Platform cannot be null");
        this.arguments.add("--target-platform " + platform);
        return this;
    }

    /**
     * Adds the `--version` option to the JMOD command.
     *
     * @return the current `JmodCLIBuilder` instance
     */
    public JmodCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Adds an argument file to the JMOD command.
     *
     * @param file the argument file to add; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the argument file is null
     */
    public JmodCLIBuilder argumentFile(Path file) {
        Objects.requireNonNull(file, "Argument file cannot be null");
        this.arguments.add("@" + file);
        return this;
    }

    /**
     * Adds the `--do-not-resolve-by-default` option to the JMOD command.
     *
     * @return the current `JmodCLIBuilder` instance
     */
    public JmodCLIBuilder doNotResolveByDefault() {
        this.arguments.add("--do-not-resolve-by-default");
        return this;
    }

    /**
     * Adds the `--warn-if-resolved` option to the JMOD command with the specified hint.
     *
     * @param hint the hint to warn if resolved; must not be null
     * @return the current `JmodCLIBuilder` instance
     * @throws NullPointerException if the hint is null
     */
    public JmodCLIBuilder warnIfResolved(String hint) {
        Objects.requireNonNull(hint, "Warning hint cannot be null");
        this.arguments.add("--warn-if-resolved=" + hint);
        return this;
    }

    @Override
    public Process run() {
        if (operationMode == null)
            throw new IllegalStateException("An operation mode must be specified.");
        if (jmodFile == null)
            throw new IllegalStateException("A JMOD file must be provided.");

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.add(operationMode.command());
        command.addAll(arguments);
        command.add(jmodFile);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jmod");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jmod process", exception);
        }
    }

    private enum OperationMode {
        CREATE("create"),
        EXTRACT("extract"),
        LIST("list"),
        DESCRIBE("describe"),
        HASH("hash");

        private final String command;

        OperationMode(String command) {
            this.command = command;
        }

        public String command() {
            return command;
        }
    }
}
