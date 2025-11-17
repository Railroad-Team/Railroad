package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    public JmodCLIBuilder create(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.CREATE;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    public JmodCLIBuilder extract(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.EXTRACT;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    public JmodCLIBuilder list(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.LIST;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    public JmodCLIBuilder describe(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.DESCRIBE;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    public JmodCLIBuilder hash(Path jmodFile) {
        Objects.requireNonNull(jmodFile, "JMOD file cannot be null");
        this.operationMode = OperationMode.HASH;
        this.jmodFile = jmodFile.toString();
        return this;
    }

    public JmodCLIBuilder classPath(String path) {
        Objects.requireNonNull(path, "Class path cannot be null");
        this.arguments.add("--class-path " + path);
        return this;
    }

    public JmodCLIBuilder cmds(Path path) {
        Objects.requireNonNull(path, "Command path cannot be null");
        this.arguments.add("--cmds " + path);
        return this;
    }

    public JmodCLIBuilder compression(String compression) {
        Objects.requireNonNull(compression, "Compression cannot be null");
        this.arguments.add("--compress " + compression);
        return this;
    }

    public JmodCLIBuilder config(Path path) {
        Objects.requireNonNull(path, "Config path cannot be null");
        this.arguments.add("--config " + path);
        return this;
    }

    public JmodCLIBuilder entryTimestamp(String timestamp) {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        this.arguments.add("--date " + timestamp);
        return this;
    }

    public JmodCLIBuilder extractionDirectory(Path path) {
        Objects.requireNonNull(path, "Extraction directory cannot be null");
        this.arguments.add("--dir " + path);
        return this;
    }

    public JmodCLIBuilder dryRun() {
        this.arguments.add("--dry-run");
        return this;
    }

    public JmodCLIBuilder exclude(String patternList) {
        Objects.requireNonNull(patternList, "Pattern list cannot be null");
        this.arguments.add("--exclude " + patternList);
        return this;
    }

    public JmodCLIBuilder hashModules(String regexPattern) {
        Objects.requireNonNull(regexPattern, "Regex pattern cannot be null");
        this.arguments.add("--hash-modules " + regexPattern);
        return this;
    }

    public JmodCLIBuilder headerFiles(Path path) {
        Objects.requireNonNull(path, "Header files path cannot be null");
        this.arguments.add("--header-files " + path);
        return this;
    }

    public JmodCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JmodCLIBuilder helpExtra() {
        this.arguments.add("--help-extra");
        return this;
    }

    public JmodCLIBuilder legalNotices(Path path) {
        Objects.requireNonNull(path, "Legal notices path cannot be null");
        this.arguments.add("--legal-notices " + path);
        return this;
    }

    public JmodCLIBuilder libs(Path path) {
        Objects.requireNonNull(path, "Libraries path cannot be null");
        this.arguments.add("--libs " + path);
        return this;
    }

    public JmodCLIBuilder mainClass(String className) {
        Objects.requireNonNull(className, "Main class cannot be null");
        this.arguments.add("--main-class " + className);
        return this;
    }

    public JmodCLIBuilder manPages(Path path) {
        Objects.requireNonNull(path, "Man pages path cannot be null");
        this.arguments.add("--man-pages " + path);
        return this;
    }

    public JmodCLIBuilder moduleVersion(String version) {
        Objects.requireNonNull(version, "Module version cannot be null");
        this.arguments.add("--module-version " + version);
        return this;
    }

    public JmodCLIBuilder modulePath(String path) {
        Objects.requireNonNull(path, "Module path cannot be null");
        this.arguments.add("--module-path " + path);
        return this;
    }

    public JmodCLIBuilder targetPlatform(String platform) {
        Objects.requireNonNull(platform, "Platform cannot be null");
        this.arguments.add("--target-platform " + platform);
        return this;
    }

    public JmodCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    public JmodCLIBuilder argumentFile(Path file) {
        Objects.requireNonNull(file, "Argument file cannot be null");
        this.arguments.add("@" + file);
        return this;
    }

    public JmodCLIBuilder doNotResolveByDefault() {
        this.arguments.add("--do-not-resolve-by-default");
        return this;
    }

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
