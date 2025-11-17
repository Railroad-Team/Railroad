package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JlinkCLIBuilder implements CLIBuilder<Process, JlinkCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jlink.exe" : "jlink";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JlinkCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    public static JlinkCLIBuilder create(JDK jdk) {
        return new JlinkCLIBuilder(jdk);
    }

    @Override
    public JlinkCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JlinkCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JlinkCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JlinkCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JlinkCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    public JlinkCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    public JlinkCLIBuilder bindServices() {
        this.arguments.add("--bind-services");
        return this;
    }

    public JlinkCLIBuilder compressionLevel(int level) {
        if (level < 0 || level > 2)
            throw new IllegalArgumentException("Compression level must be 0, 1, or 2");

        this.arguments.add("--compress=" + level);
        return this;
    }

    public JlinkCLIBuilder compressionLevel(int level, String filterPattern) {
        if (level < 0 || level > 2)
            throw new IllegalArgumentException("Compression level must be 0, 1, or 2");

        Objects.requireNonNull(filterPattern, "Filter pattern cannot be null");
        this.arguments.add("--compress=" + level + ":filter=" + filterPattern);
        return this;
    }

    public JlinkCLIBuilder disablePlugin(String pluginName) {
        Objects.requireNonNull(pluginName, "Plugin name cannot be null");
        this.arguments.add("--disable-plugin " + pluginName);
        return this;
    }

    public JlinkCLIBuilder endian(String endian) {
        Objects.requireNonNull(endian, "Endian cannot be null");
        this.arguments.add("--endian " + endian);
        return this;
    }

    public JlinkCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JlinkCLIBuilder ignoreSigningInformation() {
        this.arguments.add("--ignore-signing-information");
        return this;
    }

    public JlinkCLIBuilder launcher(String commandName, String moduleOrMain) {
        Objects.requireNonNull(commandName, "Command name cannot be null");
        Objects.requireNonNull(moduleOrMain, "Module definition cannot be null");
        this.arguments.add("--launcher " + commandName + "=" + moduleOrMain);
        return this;
    }

    public JlinkCLIBuilder limitModules(String... modules) {
        Objects.requireNonNull(modules, "Module names cannot be null");
        this.arguments.add("--limit-modules " + String.join(",", modules));
        return this;
    }

    public JlinkCLIBuilder listPlugins() {
        this.arguments.add("--list-plugins");
        return this;
    }

    public JlinkCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JlinkCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        return modulePath(Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new));
    }

    public JlinkCLIBuilder noHeaderFiles() {
        this.arguments.add("--no-header-files");
        return this;
    }

    public JlinkCLIBuilder noManPages() {
        this.arguments.add("--no-man-pages");
        return this;
    }

    public JlinkCLIBuilder output(Path path) {
        Objects.requireNonNull(path, "Output path cannot be null");
        this.arguments.add("--output " + path);
        return this;
    }

    public JlinkCLIBuilder saveOptions(Path file) {
        Objects.requireNonNull(file, "Options file cannot be null");
        this.arguments.add("--save-opts " + file);
        return this;
    }

    public JlinkCLIBuilder suggestProviders(String... serviceTypes) {
        if (serviceTypes == null || serviceTypes.length == 0) {
            this.arguments.add("--suggest-providers");
            return this;
        }

        this.arguments.add("--suggest-providers " + String.join(",", serviceTypes));
        return this;
    }

    public JlinkCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    public JlinkCLIBuilder includeLocales(String... locales) {
        Objects.requireNonNull(locales, "Locales cannot be null");
        this.arguments.add("--include-locales=" + String.join(",", locales));
        return this;
    }

    public JlinkCLIBuilder orderResources(String patternList) {
        Objects.requireNonNull(patternList, "Pattern list cannot be null");
        this.arguments.add("--order-resources=" + patternList);
        return this;
    }

    public JlinkCLIBuilder stripDebug() {
        this.arguments.add("--strip-debug");
        return this;
    }

    public JlinkCLIBuilder generateCdsArchive() {
        this.arguments.add("--generate-cds-archive");
        return this;
    }

    public JlinkCLIBuilder addArgumentFile(Path file) {
        Objects.requireNonNull(file, "Argument file cannot be null");
        this.arguments.add("@" + file);
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(EXECUTABLE_NAME).toString());
        command.addAll(arguments);

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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jlink");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jlink process", exception);
        }
    }
}
