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
 * A fluent builder for constructing and executing {@code jdeps} commands.
 * <p>
 * This builder provides methods to analyze class dependencies, including options for
 * output format, classpath and module path settings, and various filtering capabilities.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdeps.html">jdeps command documentation</a>
 */
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

    /**
     * Creates a new {@code JdepsCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code jdeps} command.
     * @return A new builder instance.
     */
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

    /**
     * Displays the help message. Corresponds to the {@code --help} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Specifies the directory for DOT file output. Corresponds to the {@code --dot-output} option.
     *
     * @param directory The output directory.
     * @return This builder instance.
     */
    public JdepsCLIBuilder dotOutput(Path directory) {
        Objects.requireNonNull(directory, "DOT output directory cannot be null");
        this.arguments.add("--dot-output " + directory);
        return this;
    }

    /**
     * Prints a summary of the dependencies. Corresponds to the {@code --summary} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder summary() {
        this.arguments.add("--summary");
        return this;
    }

    /**
     * Enables verbose output. Corresponds to the {@code --verbose} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    /**
     * Enables verbose output for package-level dependencies. Corresponds to the {@code -verbose:package} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder verbosePackages() {
        this.arguments.add("-verbose:package");
        return this;
    }

    /**
     * Enables verbose output for class-level dependencies. Corresponds to the {@code -verbose:class} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder verboseClasses() {
        this.arguments.add("-verbose:class");
        return this;
    }

    /**
     * Shows only API dependencies. Corresponds to the {@code --api-only} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder apiOnly() {
        this.arguments.add("--api-only");
        return this;
    }

    /**
     * Shows dependencies on internal JDK APIs. Corresponds to the {@code --jdk-internals} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder jdkInternals() {
        this.arguments.add("--jdk-internals");
        return this;
    }

    /**
     * Specifies the class path for analyzing dependencies. Corresponds to the {@code --class-path} option.
     *
     * @param pathEntries The class path entries.
     * @return This builder instance.
     */
    public JdepsCLIBuilder classPath(String... pathEntries) {
        Objects.requireNonNull(pathEntries, "Classpath entries cannot be null");
        this.arguments.add("--class-path " + String.join(File.pathSeparator, pathEntries));
        return this;
    }

    /**
     * Specifies the class path for analyzing dependencies. Corresponds to the {@code --class-path} option.
     *
     * @param pathEntries The class path entries.
     * @return This builder instance.
     */
    public JdepsCLIBuilder classPath(Path... pathEntries) {
        Objects.requireNonNull(pathEntries, "Classpath entries cannot be null");
        return classPath(Arrays.stream(pathEntries).map(Path::toString).toArray(String[]::new));
    }

    /**
     * Specifies the module path for analyzing dependencies. Corresponds to the {@code --module-path} option.
     *
     * @param modulePaths The module path entries.
     * @return This builder instance.
     */
    public JdepsCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Specifies the module path for analyzing dependencies. Corresponds to the {@code --module-path} option.
     *
     * @param modulePaths The module path entries.
     * @return This builder instance.
     */
    public JdepsCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        return modulePath(Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new));
    }

    /**
     * Specifies the upgrade module path. Corresponds to the {@code --upgrade-module-path} option.
     *
     * @param modulePaths The upgrade module path entries.
     * @return This builder instance.
     */
    public JdepsCLIBuilder upgradeModulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Upgrade module path entries cannot be null");
        this.arguments.add("--upgrade-module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Specifies the system module path. Corresponds to the {@code --system} option.
     *
     * @param javaHome The path to the Java home directory.
     * @return This builder instance.
     */
    public JdepsCLIBuilder systemModulePath(String javaHome) {
        Objects.requireNonNull(javaHome, "Java home cannot be null");
        this.arguments.add("--system " + javaHome);
        return this;
    }

    /**
     * Adds modules to the module graph. Corresponds to the {@code --add-modules} option.
     *
     * @param modules The names of the modules to add.
     * @return This builder instance.
     */
    public JdepsCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Module names cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Specifies the multi-release JAR file version. Corresponds to the {@code --multi-release} option.
     *
     * @param version The multi-release version.
     * @return This builder instance.
     */
    public JdepsCLIBuilder multiRelease(String version) {
        Objects.requireNonNull(version, "Multi-release version cannot be null");
        this.arguments.add("--multi-release " + version);
        return this;
    }

    /**
     * Specifies the multi-release JAR file version. Corresponds to the {@code --multi-release} option.
     *
     * @param version The multi-release version.
     * @return This builder instance.
     */
    public JdepsCLIBuilder multiRelease(int version) {
        return multiRelease(Integer.toString(version));
    }

    /**
     * Suppresses output. Corresponds to the {@code --quiet} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder quiet() {
        this.arguments.add("--quiet");
        return this;
    }

    /**
     * Prints version information and exits. Corresponds to the {@code --version} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder version() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Specifies the module to analyze. Corresponds to the {@code --module} option.
     *
     * @param moduleName The name of the module.
     * @return This builder instance.
     */
    public JdepsCLIBuilder module(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--module " + moduleName);
        return this;
    }

    /**
     * Generates a {@code module-info.java} file in the specified directory. Corresponds to the {@code --generate-module-info} option.
     *
     * @param directory The output directory.
     * @return This builder instance.
     */
    public JdepsCLIBuilder generateModuleInfo(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--generate-module-info " + directory);
        return this;
    }

    /**
     * Generates an {@code open module} declaration in the specified directory. Corresponds to the {@code --generate-open-module} option.
     *
     * @param directory The output directory.
     * @return This builder instance.
     */
    public JdepsCLIBuilder generateOpenModule(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        this.arguments.add("--generate-open-module " + directory);
        return this;
    }

    /**
     * Checks the specified modules for dependencies. Corresponds to the {@code --check} option.
     *
     * @param modules The modules to check.
     * @return This builder instance.
     */
    public JdepsCLIBuilder checkModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--check " + String.join(",", modules));
        return this;
    }

    /**
     * Lists all dependencies. Corresponds to the {@code --list-deps} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder listDependences() {
        this.arguments.add("--list-deps");
        return this;
    }

    /**
     * Lists reduced dependencies. Corresponds to the {@code --list-reduced-deps} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder listReducedDependences() {
        this.arguments.add("--list-reduced-deps");
        return this;
    }

    /**
     * Prints module dependencies. Corresponds to the {@code --print-module-deps} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder printModuleDependences() {
        this.arguments.add("--print-module-deps");
        return this;
    }

    /**
     * Ignores missing dependencies. Corresponds to the {@code --ignore-missing-deps} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder ignoreMissingDependences() {
        this.arguments.add("--ignore-missing-deps");
        return this;
    }

    /**
     * Filters by package name. Corresponds to the {@code --package} option.
     *
     * @param packageName The package name to filter by.
     * @return This builder instance.
     */
    public JdepsCLIBuilder packageFilter(String packageName) {
        Objects.requireNonNull(packageName, "Package name cannot be null");
        this.arguments.add("--package " + packageName);
        return this;
    }

    /**
     * Filters by a regular expression. Corresponds to the {@code --regex} option.
     *
     * @param regex The regular expression to filter by.
     * @return This builder instance.
     */
    public JdepsCLIBuilder regexFilter(String regex) {
        Objects.requireNonNull(regex, "Regex cannot be null");
        this.arguments.add("--regex " + regex);
        return this;
    }

    /**
     * Filters by required module. Corresponds to the {@code --require} option.
     *
     * @param moduleName The module name to filter by.
     * @return This builder instance.
     */
    public JdepsCLIBuilder requireFilter(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--require " + moduleName);
        return this;
    }

    /**
     * Filters the output by a specified pattern. Corresponds to the {@code --filter} option.
     *
     * @param pattern The filter pattern.
     * @return This builder instance.
     */
    public JdepsCLIBuilder filter(String pattern) {
        Objects.requireNonNull(pattern, "Filter pattern cannot be null");
        this.arguments.add("--filter " + pattern);
        return this;
    }

    /**
     * Filters output to show only package-level dependencies. Corresponds to the {@code -filter:package} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder filterPackage() {
        this.arguments.add("-filter:package");
        return this;
    }

    /**
     * Filters output to show only archive-level dependencies. Corresponds to the {@code -filter:archive} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder filterArchive() {
        this.arguments.add("-filter:archive");
        return this;
    }

    /**
     * Filters output to show only module-level dependencies. Corresponds to the {@code -filter:module} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder filterModule() {
        this.arguments.add("-filter:module");
        return this;
    }

    /**
     * Disables all filtering. Corresponds to the {@code -filter:none} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder filterNone() {
        this.arguments.add("-filter:none");
        return this;
    }

    /**
     * Shows missing dependencies. Corresponds to the {@code --missing-deps} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder missingDependences() {
        this.arguments.add("--missing-deps");
        return this;
    }

    /**
     * Includes dependencies matching the specified regular expression. Corresponds to the {@code -include} option.
     *
     * @param regex The regular expression to include.
     * @return This builder instance.
     */
    public JdepsCLIBuilder includePattern(String regex) {
        Objects.requireNonNull(regex, "Include pattern cannot be null");
        this.arguments.add("-include " + regex);
        return this;
    }

    /**
     * Recursively traverses dependencies. Corresponds to the {@code --recursive} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder recursive() {
        this.arguments.add("--recursive");
        return this;
    }

    /**
     * Disables recursive traversal of dependencies. Corresponds to the {@code --no-recursive} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder nonRecursive() {
        this.arguments.add("--no-recursive");
        return this;
    }

    /**
     * Shows the inverse of the dependencies. Corresponds to the {@code --inverse} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder inverse() {
        this.arguments.add("--inverse");
        return this;
    }

    /**
     * Shows the compile-time view of dependencies. Corresponds to the {@code --compile-time} option.
     *
     * @return This builder instance.
     */
    public JdepsCLIBuilder compileTimeView() {
        this.arguments.add("--compile-time");
        return this;
    }

    /**
     * Adds a target (JAR file, class file, or directory) to analyze.
     *
     * @param target The path to the target.
     * @return This builder instance.
     */
    public JdepsCLIBuilder addTarget(Path target) {
        Objects.requireNonNull(target, "Target cannot be null");
        this.targets.add(target.toString());
        return this;
    }

    /**
     * Adds a target (JAR file, class file, or directory) to analyze.
     *
     * @param target The target to add.
     * @return This builder instance.
     */
    public JdepsCLIBuilder addTarget(String target) {
        Objects.requireNonNull(target, "Target cannot be null");
        this.targets.add(target);
        return this;
    }

    /**
     * Adds multiple targets (JAR files, class files, or directories) to analyze.
     *
     * @param targetEntries The targets to add.
     * @return This builder instance.
     */
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
