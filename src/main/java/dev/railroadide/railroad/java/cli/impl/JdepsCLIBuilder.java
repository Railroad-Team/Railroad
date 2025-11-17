package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JdepsCLIBuilder implements CLIBuilder<Process, JdepsCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jdeps.exe" : "jdeps";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final List<String> targets = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JdepsCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JdepsCLIBuilder create(JDK jdk) {
        return new JdepsCLIBuilder(jdk);
    }

    @Override
    public JdepsCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JdepsCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JdepsCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JdepsCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JdepsCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JdepsCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JdepsCLIBuilder dotOutput(Path directory) {
        Objects.requireNonNull(directory, "DOT output directory cannot be null");
        this.arguments.add("--dot-output " + directory);
        return this;
    }

    public JdepsCLIBuilder summary() {
        this.arguments.add("--summary");
        return this;
    }

    public JdepsCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    public JdepsCLIBuilder verbosePackages() {
        this.arguments.add("-verbose:package");
        return this;
    }

    public JdepsCLIBuilder verboseClasses() {
        this.arguments.add("-verbose:class");
        return this;
    }

    public JdepsCLIBuilder apiOnly() {
        this.arguments.add("--api-only");
        return this;
    }

    public JdepsCLIBuilder jdkInternals() {
        this.arguments.add("--jdk-internals");
        return this;
    }

    public JdepsCLIBuilder classPath(String... pathEntries) {
        Objects.requireNonNull(pathEntries, "Classpath entries cannot be null");
        this.arguments.add("--class-path " + String.join(File.pathSeparator, pathEntries));
        return this;
    }

    public JdepsCLIBuilder classPath(Path... pathEntries) {
        Objects.requireNonNull(pathEntries, "Classpath entries cannot be null");
        return classPath(Arrays.stream(pathEntries).map(Path::toString).toArray(String[]::new));
    }

    public JdepsCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JdepsCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        return modulePath(Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new));
    }

    public JdepsCLIBuilder upgradeModulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Upgrade module path entries cannot be null");
        this.arguments.add("--upgrade-module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JdepsCLIBuilder systemModulePath(String javaHome) {
        Objects.requireNonNull(javaHome, "Java home cannot be null");
        this.arguments.add("--system " + javaHome);
        return this;
    }

    public JdepsCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Module names cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    public JdepsCLIBuilder multiRelease(String version) {
        Objects.requireNonNull(version, "Multi-release version cannot be null");
        this.arguments.add("--multi-release " + version);
        return this;
    }

    public JdepsCLIBuilder multiRelease(int version) {
        return multiRelease(Integer.toString(version));
    }

    public JdepsCLIBuilder quiet() {
        this.arguments.add("--quiet");
        return this;
    }

    public JdepsCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    public JdepsCLIBuilder module(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--module " + moduleName);
        return this;
    }

    public JdepsCLIBuilder generateModuleInfo(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--generate-module-info " + directory);
        return this;
    }

    public JdepsCLIBuilder generateOpenModule(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--generate-open-module " + directory);
        return this;
    }

    public JdepsCLIBuilder checkModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--check " + String.join(",", modules));
        return this;
    }

    public JdepsCLIBuilder listDependences() {
        this.arguments.add("--list-deps");
        return this;
    }

    public JdepsCLIBuilder listReducedDependences() {
        this.arguments.add("--list-reduced-deps");
        return this;
    }

    public JdepsCLIBuilder printModuleDependences() {
        this.arguments.add("--print-module-deps");
        return this;
    }

    public JdepsCLIBuilder ignoreMissingDependences() {
        this.arguments.add("--ignore-missing-deps");
        return this;
    }

    public JdepsCLIBuilder packageFilter(String packageName) {
        Objects.requireNonNull(packageName, "Package name cannot be null");
        this.arguments.add("--package " + packageName);
        return this;
    }

    public JdepsCLIBuilder regexFilter(String regex) {
        Objects.requireNonNull(regex, "Regex cannot be null");
        this.arguments.add("--regex " + regex);
        return this;
    }

    public JdepsCLIBuilder requireFilter(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--require " + moduleName);
        return this;
    }

    public JdepsCLIBuilder filter(String pattern) {
        Objects.requireNonNull(pattern, "Filter pattern cannot be null");
        this.arguments.add("--filter " + pattern);
        return this;
    }

    public JdepsCLIBuilder filterPackage() {
        this.arguments.add("-filter:package");
        return this;
    }

    public JdepsCLIBuilder filterArchive() {
        this.arguments.add("-filter:archive");
        return this;
    }

    public JdepsCLIBuilder filterModule() {
        this.arguments.add("-filter:module");
        return this;
    }

    public JdepsCLIBuilder filterNone() {
        this.arguments.add("-filter:none");
        return this;
    }

    public JdepsCLIBuilder missingDependences() {
        this.arguments.add("--missing-deps");
        return this;
    }

    public JdepsCLIBuilder includePattern(String regex) {
        Objects.requireNonNull(regex, "Include pattern cannot be null");
        this.arguments.add("-include " + regex);
        return this;
    }

    public JdepsCLIBuilder recursive() {
        this.arguments.add("--recursive");
        return this;
    }

    public JdepsCLIBuilder nonRecursive() {
        this.arguments.add("--no-recursive");
        return this;
    }

    public JdepsCLIBuilder inverse() {
        this.arguments.add("--inverse");
        return this;
    }

    public JdepsCLIBuilder compileTimeView() {
        this.arguments.add("--compile-time");
        return this;
    }

    public JdepsCLIBuilder addTarget(Path target) {
        Objects.requireNonNull(target, "Target cannot be null");
        this.targets.add(target.toString());
        return this;
    }

    public JdepsCLIBuilder addTarget(String target) {
        Objects.requireNonNull(target, "Target cannot be null");
        this.targets.add(target);
        return this;
    }

    public JdepsCLIBuilder addTargets(String... targetEntries) {
        Objects.requireNonNull(targetEntries, "Targets cannot be null");
        Collections.addAll(this.targets, targetEntries);
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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jdeps");
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jdeps process", exception);
        }
    }
}
