package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A fluent builder for creating and executing {@code jar} commands.
 * <p>
 * This builder provides methods for specifying all major operations of the {@code jar} tool,
 * such as creating, updating, listing, and extracting archives. It supports setting various
 * options like the main class, manifest, module version, and file entries.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jar.html">jar command documentation</a>
 */
public class JarCLIBuilder implements CLIBuilder<Process, JarCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jar.exe" : "jar";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> fileEntries = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private OperationMode operationMode;
    private String generateIndexTarget;

    private JarCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a new {@link JarCLIBuilder} for the specified JDK.
     *
     * @param jdk The JDK to use for running the {@code jar} command.
     * @return A new builder instance.
     */
    public static JarCLIBuilder create(JDK jdk) {
        return new JarCLIBuilder(jdk);
    }

    @Override
    public JarCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JarCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JarCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JarCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JarCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    private JarCLIBuilder operation(OperationMode mode) {
        this.operationMode = Objects.requireNonNull(mode, "Operation mode cannot be null");
        if (mode != OperationMode.GENERATE_INDEX) {
            this.generateIndexTarget = null;
        }

        return this;
    }

    /**
     * Sets the operation mode to create a new archive ({@code --create}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder createArchive() {
        return operation(OperationMode.CREATE);
    }

    /**
     * Sets the operation mode to list the contents of an archive ({@code --list}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder listContents() {
        return operation(OperationMode.LIST);
    }

    /**
     * Sets the operation mode to update an existing archive ({@code --update}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder updateArchive() {
        return operation(OperationMode.UPDATE);
    }

    /**
     * Sets the operation mode to extract files from an archive ({@code --extract}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder extractArchive() {
        return operation(OperationMode.EXTRACT);
    }

    /**
     * Sets the operation mode to describe the module details of an archive ({@code --describe-module}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder describeModule() {
        return operation(OperationMode.DESCRIBE_MODULE);
    }

    /**
     * Sets the operation mode to validate an archive ({@code --validate}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder validateArchive() {
        return operation(OperationMode.VALIDATE);
    }

    /**
     * Sets the operation mode to generate index information for a set of JAR files ({@code --generate-index}).
     *
     * @param jarFile The main JAR file for which the index is generated.
     * @return This builder instance.
     */
    public JarCLIBuilder generateIndex(Path jarFile) {
        Objects.requireNonNull(jarFile, "JAR file path cannot be null");
        this.generateIndexTarget = jarFile.toString();
        return operation(OperationMode.GENERATE_INDEX);
    }

    /**
     * Specifies the archive file name ({@code --file}).
     *
     * @param jarFile The path to the JAR file.
     * @return This builder instance.
     */
    public JarCLIBuilder archiveFile(Path jarFile) {
        Objects.requireNonNull(jarFile, "Archive file path cannot be null");
        this.arguments.add("--file " + jarFile);
        return this;
    }

    /**
     * Specifies the archive file name ({@code --file}).
     *
     * @param jarFile The path to the JAR file as a string.
     * @return This builder instance.
     */
    public JarCLIBuilder archiveFile(String jarFile) {
        Objects.requireNonNull(jarFile, "Archive file path cannot be null");
        this.arguments.add("--file " + jarFile);
        return this;
    }

    /**
     * Adds multi-release versioned entries to the JAR ({@code --release}).
     *
     * @param version The release version (must be 9 or greater).
     * @return This builder instance.
     */
    public JarCLIBuilder releaseEntries(int version) {
        if (version < 9)
            throw new IllegalArgumentException("Release version must be 9 or greater");

        this.fileEntries.add("--release " + version);
        return this;
    }

    /**
     * Enables verbose output ({@code --verbose}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    /**
     * Sets the main class for an executable JAR ({@code --main-class}).
     *
     * @param className The fully qualified name of the main class.
     * @return This builder instance.
     */
    public JarCLIBuilder mainClass(String className) {
        Objects.requireNonNull(className, "Main class cannot be null");
        this.arguments.add("--main-class " + className);
        return this;
    }

    /**
     * Includes a custom manifest file ({@code --manifest}).
     *
     * @param manifestPath The path to the manifest file.
     * @return This builder instance.
     */
    public JarCLIBuilder manifest(Path manifestPath) {
        Objects.requireNonNull(manifestPath, "Manifest path cannot be null");
        this.arguments.add("--manifest " + manifestPath);
        return this;
    }

    /**
     * Includes a custom manifest file ({@code --manifest}).
     *
     * @param manifestPath The path to the manifest file as a string.
     * @return This builder instance.
     */
    public JarCLIBuilder manifest(String manifestPath) {
        Objects.requireNonNull(manifestPath, "Manifest path cannot be null");
        this.arguments.add("--manifest " + manifestPath);
        return this;
    }

    /**
     * Prevents the creation of a manifest file for the entries ({@code --no-manifest}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder noManifest() {
        this.arguments.add("--no-manifest");
        return this;
    }

    /**
     * Sets the module version for a modular JAR ({@code --module-version}).
     *
     * @param version The module version string.
     * @return This builder instance.
     */
    public JarCLIBuilder moduleVersion(String version) {
        Objects.requireNonNull(version, "Module version cannot be null");
        this.arguments.add("--module-version " + version);
        return this;
    }

    /**
     * Computes and records hashes of modules matched by the given pattern ({@code --hash-modules}).
     *
     * @param pattern The module hashing pattern.
     * @return This builder instance.
     */
    public JarCLIBuilder hashModules(String pattern) {
        Objects.requireNonNull(pattern, "Module hash pattern cannot be null");
        this.arguments.add("--hash-modules " + pattern);
        return this;
    }

    /**
     * Specifies the module path ({@code --module-path}).
     *
     * @param modulePaths An array of paths to modules.
     * @return This builder instance.
     */
    public JarCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Specifies the module path ({@code --module-path}).
     *
     * @param modulePaths An array of paths to modules.
     * @return This builder instance.
     */
    public JarCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    /**
     * Reads additional arguments from a file ({@code @file}).
     *
     * @param argFilePath The path to the argument file.
     * @return This builder instance.
     */
    public JarCLIBuilder argumentFile(Path argFilePath) {
        Objects.requireNonNull(argFilePath, "Argument file path cannot be null");
        this.arguments.add("@" + argFilePath);
        return this;
    }

    /**
     * Disables compression for the archive ({@code --no-compress}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder noCompress() {
        this.arguments.add("--no-compress");
        return this;
    }

    /**
     * Sets a timestamp for all entries in the archive ({@code --date}).
     *
     * @param isoTimestamp The timestamp in ISO-8601 format.
     * @return This builder instance.
     */
    public JarCLIBuilder entryTimestamp(String isoTimestamp) {
        Objects.requireNonNull(isoTimestamp, "Timestamp cannot be null");
        this.arguments.add("--date " + isoTimestamp);
        return this;
    }

    /**
     * Displays the help message ({@code --help}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Displays the compatibility help message ({@code --help:compat}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder helpCompat() {
        this.arguments.add("--help:compat");
        return this;
    }

    /**
     * Displays extra help information ({@code --help-extra}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder helpExtra() {
        this.arguments.add("--help-extra");
        return this;
    }

    /**
     * Displays the version information ({@code --version}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder versionInfo() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Changes the directory during execution ({@code -C}).
     *
     * @param directory The directory to change to.
     * @return This builder instance.
     */
    public JarCLIBuilder changeDirectory(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.fileEntries.add("-C " + directory);
        return this;
    }

    /**
     * Changes the directory during execution ({@code -C}).
     *
     * @param directory The directory to change to.
     * @return This builder instance.
     */
    public JarCLIBuilder changeDirectory(String directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.fileEntries.add("-C " + directory);
        return this;
    }

    /**
     * Adds a file to be included in the archive.
     *
     * @param filePath The path to the file.
     * @return This builder instance.
     */
    public JarCLIBuilder addFile(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.fileEntries.add(filePath.toString());
        return this;
    }

    /**
     * Adds a file to be included in the archive.
     *
     * @param filePath The path to the file as a string.
     * @return This builder instance.
     */
    public JarCLIBuilder addFile(String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.fileEntries.add(filePath);
        return this;
    }

    /**
     * Adds multiple files to be included in the archive.
     *
     * @param files An array of file paths.
     * @return This builder instance.
     */
    public JarCLIBuilder addFiles(String... files) {
        Objects.requireNonNull(files, "Files cannot be null");
        for (String file : files) {
            addFile(file);
        }

        return this;
    }

    /**
     * Adds multiple files to be included in the archive.
     *
     * @param files An array of file paths.
     * @return This builder instance.
     */
    public JarCLIBuilder addFiles(Path... files) {
        Objects.requireNonNull(files, "Files cannot be null");
        for (Path file : files) {
            addFile(file);
        }

        return this;
    }

    /**
     * Specifies the destination directory for extracted files ({@code --dir}).
     *
     * @param directory The destination directory.
     * @return This builder instance.
     */
    public JarCLIBuilder destinationDirectory(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--dir " + directory);
        return this;
    }

    /**
     * Specifies the destination directory for extracted files ({@code --dir}).
     *
     * @param directory The destination directory as a string.
     * @return This builder instance.
     */
    public JarCLIBuilder destinationDirectory(String directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--dir " + directory);
        return this;
    }

    /**
     * Keeps existing files when extracting ({@code --keep-old-files}).
     *
     * @return This builder instance.
     */
    public JarCLIBuilder keepOldFiles() {
        this.arguments.add("--keep-old-files");
        return this;
    }

    @Override
    public Process run() {
        if (operationMode == null)
            throw new IllegalStateException("An operation mode must be specified before running the jar command.");

        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());

        if (operationMode == OperationMode.GENERATE_INDEX) {
            if (generateIndexTarget == null)
                throw new IllegalStateException("Generate-index operation requires a target jar file.");

            command.add(operationMode.getFlag() + "=" + generateIndexTarget);
        } else {
            command.add(operationMode.getFlag());
        }

        command.addAll(arguments);
        command.addAll(fileEntries);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jar");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jar process", exception);
        }
    }

    /**
     * Represents the primary operation modes for the {@code jar} command.
     */
    @Getter
    public enum OperationMode {
        /**
         * Corresponds to the {@code --create} flag.
         */
        CREATE("--create"),
        /**
         * Corresponds to the {@code --list} flag.
         */
        LIST("--list"),
        /**
         * Corresponds to the {@code --update} flag.
         */
        UPDATE("--update"),
        /**
         * Corresponds to the {@code --extract} flag.
         */
        EXTRACT("--extract"),
        /**
         * Corresponds to the {@code --validate} flag.
         */
        VALIDATE("--validate"),
        /**
         * Corresponds to the {@code --describe-module} flag.
         */
        DESCRIBE_MODULE("--describe-module"),
        /**
         * Corresponds to the {@code --generate-index} flag.
         */
        GENERATE_INDEX("--generate-index");

        private final String flag;

        OperationMode(String flag) {
            this.flag = flag;
        }
    }
}
