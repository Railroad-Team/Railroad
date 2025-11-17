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
 * Builder to construct {@code jlink} commands for creating custom runtime images.
 * <p>
 * Allows configuration of modules, compression, plugins, launchers, and other packaging flags.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jlink.html">jlink command documentation</a>
 */
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

    /**
     * Adds the specified modules to the `jlink` command.
     *
     * @param modules the modules to add; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the modules are null
     */
    public JlinkCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Adds the `--bind-services` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder bindServices() {
        this.arguments.add("--bind-services");
        return this;
    }

    /**
     * Sets the compression level for the `jlink` command.
     *
     * @param level the compression level (0, 1, or 2)
     * @return the current `JlinkCLIBuilder` instance
     * @throws IllegalArgumentException if the level is not 0, 1, or 2
     */
    public JlinkCLIBuilder compressionLevel(int level) {
        if (level < 0 || level > 2)
            throw new IllegalArgumentException("Compression level must be 0, 1, or 2");

        this.arguments.add("--compress=" + level);
        return this;
    }

    /**
     * Sets the compression level and filter pattern for the `jlink` command.
     *
     * @param level         the compression level (0, 1, or 2)
     * @param filterPattern the filter pattern; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws IllegalArgumentException if the level is not 0, 1, or 2
     * @throws NullPointerException     if the filter pattern is null
     */
    public JlinkCLIBuilder compressionLevel(int level, String filterPattern) {
        if (level < 0 || level > 2)
            throw new IllegalArgumentException("Compression level must be 0, 1, or 2");

        Objects.requireNonNull(filterPattern, "Filter pattern cannot be null");
        this.arguments.add("--compress=" + level + ":filter=" + filterPattern);
        return this;
    }

    /**
     * Disables the specified plugin in the `jlink` command.
     *
     * @param pluginName the name of the plugin to disable; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the plugin name is null
     */
    public JlinkCLIBuilder disablePlugin(String pluginName) {
        Objects.requireNonNull(pluginName, "Plugin name cannot be null");
        this.arguments.add("--disable-plugin " + pluginName);
        return this;
    }

    /**
     * Sets the endian option for the `jlink` command.
     *
     * @param endian the endian value; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the endian value is null
     */
    public JlinkCLIBuilder endian(String endian) {
        Objects.requireNonNull(endian, "Endian cannot be null");
        this.arguments.add("--endian " + endian);
        return this;
    }

    /**
     * Adds the `--help` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Adds the `--ignore-signing-information` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder ignoreSigningInformation() {
        this.arguments.add("--ignore-signing-information");
        return this;
    }

    /**
     * Adds a launcher definition to the `jlink` command.
     *
     * @param commandName  the name of the launcher command; must not be null
     * @param moduleOrMain the module or main class definition; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the command name or module definition is null
     */
    public JlinkCLIBuilder launcher(String commandName, String moduleOrMain) {
        Objects.requireNonNull(commandName, "Command name cannot be null");
        Objects.requireNonNull(moduleOrMain, "Module definition cannot be null");
        this.arguments.add("--launcher " + commandName + "=" + moduleOrMain);
        return this;
    }

    /**
     * Limits the modules included in the `jlink` command.
     *
     * @param modules the modules to include; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the modules are null
     */
    public JlinkCLIBuilder limitModules(String... modules) {
        Objects.requireNonNull(modules, "Module names cannot be null");
        this.arguments.add("--limit-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Adds the `--list-plugins` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder listPlugins() {
        this.arguments.add("--list-plugins");
        return this;
    }

    /**
     * Sets the module path for the `jlink` command.
     *
     * @param modulePaths the module path entries; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the module path entries are null
     */
    public JlinkCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Sets the module path for the `jlink` command using `Path` objects.
     *
     * @param modulePaths the module path entries as `Path` objects; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the module path entries are null
     */
    public JlinkCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        return modulePath(Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new));
    }

    /**
     * Adds the `--no-header-files` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder noHeaderFiles() {
        this.arguments.add("--no-header-files");
        return this;
    }

    /**
     * Adds the `--no-man-pages` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder noManPages() {
        this.arguments.add("--no-man-pages");
        return this;
    }

    /**
     * Sets the output directory for the `jlink` command.
     *
     * @param path the output directory; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the output path is null
     */
    public JlinkCLIBuilder output(Path path) {
        Objects.requireNonNull(path, "Output path cannot be null");
        this.arguments.add("--output " + path);
        return this;
    }

    /**
     * Saves the options used in the `jlink` command to a file.
     *
     * @param file the file to save the options to; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the file is null
     */
    public JlinkCLIBuilder saveOptions(Path file) {
        Objects.requireNonNull(file, "Options file cannot be null");
        this.arguments.add("--save-opts " + file);
        return this;
    }

    /**
     * Suggests providers for the `jlink` command.
     *
     * @param serviceTypes the service types to suggest providers for; can be empty
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder suggestProviders(String... serviceTypes) {
        if (serviceTypes == null || serviceTypes.length == 0) {
            this.arguments.add("--suggest-providers");
            return this;
        }

        this.arguments.add("--suggest-providers " + String.join(",", serviceTypes));
        return this;
    }

    /**
     * Adds the `--version` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Includes the specified locales in the `jlink` command.
     *
     * @param locales the locales to include; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the locales are null
     */
    public JlinkCLIBuilder includeLocales(String... locales) {
        Objects.requireNonNull(locales, "Locales cannot be null");
        this.arguments.add("--include-locales=" + String.join(",", locales));
        return this;
    }

    /**
     * Sets the resource ordering pattern for the `jlink` command.
     *
     * @param patternList the pattern list; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the pattern list is null
     */
    public JlinkCLIBuilder orderResources(String patternList) {
        Objects.requireNonNull(patternList, "Pattern list cannot be null");
        this.arguments.add("--order-resources=" + patternList);
        return this;
    }

    /**
     * Adds the `--strip-debug` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder stripDebug() {
        this.arguments.add("--strip-debug");
        return this;
    }

    /**
     * Adds the `--generate-cds-archive` option to the `jlink` command.
     *
     * @return the current `JlinkCLIBuilder` instance
     */
    public JlinkCLIBuilder generateCdsArchive() {
        this.arguments.add("--generate-cds-archive");
        return this;
    }

    /**
     * Adds an argument file to the `jlink` command.
     *
     * @param file the argument file; must not be null
     * @return the current `JlinkCLIBuilder` instance
     * @throws NullPointerException if the argument file is null
     */
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
