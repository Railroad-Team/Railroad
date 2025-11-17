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

public class JshellCLIBuilder implements CLIBuilder<Process, JshellCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jshell.exe" : "jshell";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> loadFiles = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JshellCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JshellCLIBuilder create(JDK jdk) {
        return new JshellCLIBuilder(jdk);
    }

    @Override
    public JshellCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JshellCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JshellCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JshellCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JshellCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JshellCLIBuilder addExports(String moduleAndPackage) {
        Objects.requireNonNull(moduleAndPackage, "Module/package cannot be null");
        this.arguments.add("--add-exports " + moduleAndPackage);
        return this;
    }

    public JshellCLIBuilder addExports(String moduleName, String packageName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        return addExports(moduleName + "/" + packageName);
    }

    public JshellCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        if (modules.length == 0)
            throw new IllegalArgumentException("At least one module must be provided");

        for (String module : modules) {
            Objects.requireNonNull(module, "Module name cannot be null");
        }

        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    public JshellCLIBuilder compilerOptions(String... options) {
        Objects.requireNonNull(options, "Compiler options cannot be null");
        for (String option : options) {
            Objects.requireNonNull(option, "Compiler option cannot be null");
            this.arguments.add("-C" + option);
        }

        return this;
    }

    public JshellCLIBuilder classPath(String... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        if (entries.length == 0)
            throw new IllegalArgumentException("At least one classpath entry must be provided");

        this.arguments.add("--class-path " + String.join(File.pathSeparator, entries));
        return this;
    }

    public JshellCLIBuilder classPath(Path... entries) {
        Objects.requireNonNull(entries, "Classpath entries cannot be null");
        String[] entryStrings = Arrays.stream(entries).map(path -> Objects.requireNonNull(path, "Classpath path cannot be null").toString()).toArray(String[]::new);
        return classPath(entryStrings);
    }

    public JshellCLIBuilder enablePreview() {
        this.arguments.add("--enable-preview");
        return this;
    }

    public JshellCLIBuilder executionEngine(String specification) {
        Objects.requireNonNull(specification, "Execution specification cannot be null");
        this.arguments.add("--execution " + specification);
        return this;
    }

    public JshellCLIBuilder feedbackMode(String mode) {
        Objects.requireNonNull(mode, "Feedback mode cannot be null");
        this.arguments.add("--feedback " + mode);
        return this;
    }

    public JshellCLIBuilder feedbackMode(FeedbackMode mode) {
        Objects.requireNonNull(mode, "Feedback mode cannot be null");
        return feedbackMode(mode.getMode());
    }

    public JshellCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JshellCLIBuilder helpExtra() {
        this.arguments.add("--help-extra");
        return this;
    }

    public JshellCLIBuilder jvmOptions(String... options) {
        Objects.requireNonNull(options, "JVM options cannot be null");
        for (String option : options) {
            Objects.requireNonNull(option, "JVM option cannot be null");
            this.arguments.add("-J" + option);
        }

        return this;
    }

    public JshellCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        if (modulePaths.length == 0)
            throw new IllegalArgumentException("At least one module path must be provided");

        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JshellCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(path -> Objects.requireNonNull(path, "Module path cannot be null").toString()).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    public JshellCLIBuilder noStartupScripts() {
        this.arguments.add("--no-startup");
        return this;
    }

    public JshellCLIBuilder conciseFeedback() {
        this.arguments.add("-q");
        return this;
    }

    public JshellCLIBuilder runtimeOptions(String... options) {
        Objects.requireNonNull(options, "Runtime options cannot be null");
        for (String option : options) {
            Objects.requireNonNull(option, "Runtime option cannot be null");
            this.arguments.add("-R" + option);
        }

        return this;
    }

    public JshellCLIBuilder silentFeedback() {
        this.arguments.add("-s");
        return this;
    }

    public JshellCLIBuilder showVersionAndEnter() {
        this.arguments.add("--show-version");
        return this;
    }

    public JshellCLIBuilder startupScript(String scriptName) {
        Objects.requireNonNull(scriptName, "Startup script cannot be null");
        this.arguments.add("--startup " + scriptName);
        return this;
    }

    public JshellCLIBuilder startupScript(Path scriptPath) {
        Objects.requireNonNull(scriptPath, "Startup script path cannot be null");
        return startupScript(scriptPath.toString());
    }

    public JshellCLIBuilder verboseFeedback() {
        this.arguments.add("-v");
        return this;
    }

    public JshellCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    public JshellCLIBuilder addLoadFile(String loadFile) {
        Objects.requireNonNull(loadFile, "Load file cannot be null");
        this.loadFiles.add(loadFile);
        return this;
    }

    public JshellCLIBuilder addLoadFile(Path path) {
        Objects.requireNonNull(path, "Load file path cannot be null");
        return addLoadFile(path.toString());
    }

    public JshellCLIBuilder acceptInputFromStdin() {
        this.loadFiles.add("-");
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        command.addAll(loadFiles);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jshell");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jshell process", exception);
        }
    }

    @Getter
    public enum FeedbackMode {
        VERBOSE("verbose"),
        NORMAL("normal"),
        CONCISE("concise"),
        SILENT("silent"),
        CUSTOM("custom");

        private final String mode;

        FeedbackMode(String mode) {
            this.mode = mode;
        }
    }
}
