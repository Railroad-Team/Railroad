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

    public JarCLIBuilder createArchive() {
        return operation(OperationMode.CREATE);
    }

    public JarCLIBuilder listContents() {
        return operation(OperationMode.LIST);
    }

    public JarCLIBuilder updateArchive() {
        return operation(OperationMode.UPDATE);
    }

    public JarCLIBuilder extractArchive() {
        return operation(OperationMode.EXTRACT);
    }

    public JarCLIBuilder describeModule() {
        return operation(OperationMode.DESCRIBE_MODULE);
    }

    public JarCLIBuilder validateArchive() {
        return operation(OperationMode.VALIDATE);
    }

    public JarCLIBuilder generateIndex(Path jarFile) {
        Objects.requireNonNull(jarFile, "JAR file path cannot be null");
        this.generateIndexTarget = jarFile.toString();
        return operation(OperationMode.GENERATE_INDEX);
    }

    public JarCLIBuilder archiveFile(Path jarFile) {
        Objects.requireNonNull(jarFile, "Archive file path cannot be null");
        this.arguments.add("--file " + jarFile);
        return this;
    }

    public JarCLIBuilder archiveFile(String jarFile) {
        Objects.requireNonNull(jarFile, "Archive file path cannot be null");
        this.arguments.add("--file " + jarFile);
        return this;
    }

    public JarCLIBuilder releaseEntries(int version) {
        if (version < 9)
            throw new IllegalArgumentException("Release version must be 9 or greater");

        this.fileEntries.add("--release " + version);
        return this;
    }

    public JarCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    public JarCLIBuilder mainClass(String className) {
        Objects.requireNonNull(className, "Main class cannot be null");
        this.arguments.add("--main-class " + className);
        return this;
    }

    public JarCLIBuilder manifest(Path manifestPath) {
        Objects.requireNonNull(manifestPath, "Manifest path cannot be null");
        this.arguments.add("--manifest " + manifestPath);
        return this;
    }

    public JarCLIBuilder manifest(String manifestPath) {
        Objects.requireNonNull(manifestPath, "Manifest path cannot be null");
        this.arguments.add("--manifest " + manifestPath);
        return this;
    }

    public JarCLIBuilder noManifest() {
        this.arguments.add("--no-manifest");
        return this;
    }

    public JarCLIBuilder moduleVersion(String version) {
        Objects.requireNonNull(version, "Module version cannot be null");
        this.arguments.add("--module-version " + version);
        return this;
    }

    public JarCLIBuilder hashModules(String pattern) {
        Objects.requireNonNull(pattern, "Module hash pattern cannot be null");
        this.arguments.add("--hash-modules " + pattern);
        return this;
    }

    public JarCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JarCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    public JarCLIBuilder argumentFile(Path argFilePath) {
        Objects.requireNonNull(argFilePath, "Argument file path cannot be null");
        this.arguments.add("@" + argFilePath);
        return this;
    }

    public JarCLIBuilder noCompress() {
        this.arguments.add("--no-compress");
        return this;
    }

    public JarCLIBuilder entryTimestamp(String isoTimestamp) {
        Objects.requireNonNull(isoTimestamp, "Timestamp cannot be null");
        this.arguments.add("--date " + isoTimestamp);
        return this;
    }

    public JarCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JarCLIBuilder helpCompat() {
        this.arguments.add("--help:compat");
        return this;
    }

    public JarCLIBuilder helpExtra() {
        this.arguments.add("--help-extra");
        return this;
    }

    public JarCLIBuilder versionInfo() {
        this.arguments.add("--version");
        return this;
    }

    public JarCLIBuilder changeDirectory(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.fileEntries.add("-C " + directory);
        return this;
    }

    public JarCLIBuilder changeDirectory(String directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.fileEntries.add("-C " + directory);
        return this;
    }

    public JarCLIBuilder addFile(Path filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.fileEntries.add(filePath.toString());
        return this;
    }

    public JarCLIBuilder addFile(String filePath) {
        Objects.requireNonNull(filePath, "File path cannot be null");
        this.fileEntries.add(filePath);
        return this;
    }

    public JarCLIBuilder addFiles(String... files) {
        Objects.requireNonNull(files, "Files cannot be null");
        for (String file : files) {
            addFile(file);
        }

        return this;
    }

    public JarCLIBuilder addFiles(Path... files) {
        Objects.requireNonNull(files, "Files cannot be null");
        for (Path file : files) {
            addFile(file);
        }

        return this;
    }

    public JarCLIBuilder destinationDirectory(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--dir " + directory);
        return this;
    }

    public JarCLIBuilder destinationDirectory(String directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--dir " + directory);
        return this;
    }

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

    @Getter
    public enum OperationMode {
        CREATE("--create"),
        LIST("--list"),
        UPDATE("--update"),
        EXTRACT("--extract"),
        VALIDATE("--validate"),
        DESCRIBE_MODULE("--describe-module"),
        GENERATE_INDEX("--generate-index");

        private final String flag;

        OperationMode(String flag) {
            this.flag = flag;
        }
    }
}
