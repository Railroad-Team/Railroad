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

public class JavapCLIBuilder implements CLIBuilder<Process, JavapCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "javap.exe" : "javap";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> classTargets = new ArrayList<>();
    private Path workingDirectory;
    private final Map<String, String> environmentVariables = new HashMap<>();
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JavapCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JavapCLIBuilder create(JDK jdk) {
        return new JavapCLIBuilder(jdk);
    }

    @Override
    public JavapCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JavapCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JavapCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JavapCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JavapCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JavapCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JavapCLIBuilder version() {
        this.arguments.add("-version");
        return this;
    }

    public JavapCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    public JavapCLIBuilder lineAndLocalVariableTables() {
        this.arguments.add("-l");
        return this;
    }

    public JavapCLIBuilder visibility(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add(visibility.getFlag());
        return this;
    }

    public JavapCLIBuilder disassembleCode() {
        this.arguments.add("-c");
        return this;
    }

    public JavapCLIBuilder printSignatures() {
        this.arguments.add("-s");
        return this;
    }

    public JavapCLIBuilder showSystemInfo() {
        this.arguments.add("-sysinfo");
        return this;
    }

    public JavapCLIBuilder verifyClasses() {
        this.arguments.add("-verify");
        return this;
    }

    public JavapCLIBuilder showConstants() {
        this.arguments.add("-constants");
        return this;
    }

    public JavapCLIBuilder module(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--module " + moduleName);
        return this;
    }

    public JavapCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JavapCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    public JavapCLIBuilder systemModules(String systemPath) {
        Objects.requireNonNull(systemPath, "System module path cannot be null");
        this.arguments.add("--system " + systemPath);
        return this;
    }

    public JavapCLIBuilder classpath(String... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        this.arguments.add("-cp " + String.join(File.pathSeparator, classpathEntries));
        return this;
    }

    public JavapCLIBuilder classpath(Path... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        String[] entryStrings = Arrays.stream(classpathEntries).map(Path::toString).toArray(String[]::new);
        return classpath(entryStrings);
    }

    public JavapCLIBuilder bootClassPath(String... bootClassPathEntries) {
        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        this.arguments.add("-bootclasspath " + String.join(File.pathSeparator, bootClassPathEntries));
        return this;
    }

    public JavapCLIBuilder bootClassPath(Path... bootClassPathEntries) {
        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        String[] entryStrings = Arrays.stream(bootClassPathEntries).map(Path::toString).toArray(String[]::new);
        return bootClassPath(entryStrings);
    }

    public JavapCLIBuilder multiRelease(String version) {
        Objects.requireNonNull(version, "Multi-release version cannot be null");
        this.arguments.add("--multi-release " + version);
        return this;
    }

    public JavapCLIBuilder multiRelease(int version) {
        return multiRelease(Integer.toString(version));
    }

    public JavapCLIBuilder jvmOptions(String... options) {
        Objects.requireNonNull(options, "JVM options cannot be null");
        for (String option : options) {
            Objects.requireNonNull(option, "JVM option cannot be null");
            this.arguments.add("-J" + option);
        }

        return this;
    }

    public JavapCLIBuilder addClassName(String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        this.classTargets.add(className);
        return this;
    }

    public JavapCLIBuilder addClassNames(String... classNames) {
        Objects.requireNonNull(classNames, "Class names cannot be null");
        for (String className : classNames) {
            addClassName(className);
        }

        return this;
    }

    public JavapCLIBuilder addClassFile(Path classFilePath) {
        Objects.requireNonNull(classFilePath, "Class file path cannot be null");
        this.classTargets.add(classFilePath.toString());
        return this;
    }

    public JavapCLIBuilder addClassFiles(Path... classFilePaths) {
        Objects.requireNonNull(classFilePaths, "Class file paths cannot be null");
        for (Path classFilePath : classFilePaths) {
            addClassFile(classFilePath);
        }

        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);
        command.addAll(classTargets);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "javap");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start javap process", exception);
        }
    }

    @Getter
    public enum Visibility {
        PUBLIC("-public"),
        PROTECTED("-protected"),
        PACKAGE("-package"),
        PRIVATE("-private");

        private final String flag;

        Visibility(String flag) {
            this.flag = flag;
        }
    }
}
