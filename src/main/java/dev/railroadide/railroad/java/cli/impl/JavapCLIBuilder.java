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
 * A fluent builder for constructing and executing {@code javap} commands.
 * <p>
 * This builder provides methods to configure various options for disassembling class files,
 * including verbosity, visibility filters, and classpath settings.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html">javap command documentation</a>
 */
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

    /**
     * Creates a new {@code JavapCLIBuilder} instance.
     *
     * @param jdk The JDK to use for executing the {@code javap} command.
     * @return A new builder instance.
     */
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

    /**
     * Displays the help message. Corresponds to the {@code --help} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Displays version information. Corresponds to the {@code -version} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder version() {
        this.arguments.add("-version");
        return this;
    }

    /**
     * Enables verbose output, including stack size, number of locals, and args for methods. Corresponds to the {@code -verbose} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder verbose() {
        this.arguments.add("-verbose");
        return this;
    }

    /**
     * Prints line number and local variable tables. Corresponds to the {@code -l} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder lineAndLocalVariableTables() {
        this.arguments.add("-l");
        return this;
    }

    /**
     * Specifies the minimum visibility level of members to print. Corresponds to options like {@code -public}, {@code -protected}, {@code -package}, {@code -private}.
     *
     * @param visibility The minimum visibility level.
     * @return This builder instance.
     */
    public JavapCLIBuilder visibility(Visibility visibility) {
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        this.arguments.add(visibility.getFlag());
        return this;
    }

    /**
     * Disassembles the code. Corresponds to the {@code -c} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder disassembleCode() {
        this.arguments.add("-c");
        return this;
    }

    /**
     * Prints internal type signatures. Corresponds to the {@code -s} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder printSignatures() {
        this.arguments.add("-s");
        return this;
    }

    /**
     * Shows system information. Corresponds to the {@code -sysinfo} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder showSystemInfo() {
        this.arguments.add("-sysinfo");
        return this;
    }

    /**
     * Verifies the reachability of classes. Corresponds to the {@code -verify} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder verifyClasses() {
        this.arguments.add("-verify");
        return this;
    }

    /**
     * Shows final static constants. Corresponds to the {@code -constants} option.
     *
     * @return This builder instance.
     */
    public JavapCLIBuilder showConstants() {
        this.arguments.add("-constants");
        return this;
    }

    /**
     * Specifies the module to inspect. Corresponds to the {@code --module} option.
     *
     * @param moduleName The name of the module.
     * @return This builder instance.
     */
    public JavapCLIBuilder module(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--module " + moduleName);
        return this;
    }

    /**
     * Specifies the module path. Corresponds to the {@code --module-path} option.
     *
     * @param modulePaths The entries for the module path.
     * @return This builder instance.
     */
    public JavapCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Specifies the module path. Corresponds to the {@code --module-path} option.
     *
     * @param modulePaths The entries for the module path.
     * @return This builder instance.
     */
    public JavapCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module paths cannot be null");
        String[] pathStrings = Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new);
        return modulePath(pathStrings);
    }

    /**
     * Specifies the path to the system modules. Corresponds to the {@code --system} option.
     *
     * @param systemPath The path to the system modules.
     * @return This builder instance.
     */
    public JavapCLIBuilder systemModules(String systemPath) {
        Objects.requireNonNull(systemPath, "System module path cannot be null");
        this.arguments.add("--system " + systemPath);
        return this;
    }

    /**
     * Specifies the classpath for finding class files. Corresponds to the {@code -cp} or {@code -classpath} option.
     *
     * @param classpathEntries The entries for the classpath.
     * @return This builder instance.
     */
    public JavapCLIBuilder classpath(String... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        this.arguments.add("-cp " + String.join(File.pathSeparator, classpathEntries));
        return this;
    }

    /**
     * Specifies the classpath for finding class files. Corresponds to the {@code -cp} or {@code -classpath} option.
     *
     * @param classpathEntries The entries for the classpath.
     * @return This builder instance.
     */
    public JavapCLIBuilder classpath(Path... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        String[] entryStrings = Arrays.stream(classpathEntries).map(Path::toString).toArray(String[]::new);
        return classpath(entryStrings);
    }

    /**
     * Specifies the boot classpath. Corresponds to the {@code -bootclasspath} option.
     *
     * @param bootClassPathEntries The entries for the boot classpath.
     * @return This builder instance.
     */
    public JavapCLIBuilder bootClassPath(String... bootClassPathEntries) {
        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        this.arguments.add("-bootclasspath " + String.join(File.pathSeparator, bootClassPathEntries));
        return this;
    }

    /**
     * Specifies the boot classpath. Corresponds to the {@code -bootclasspath} option.
     *
     * @param bootClassPathEntries The entries for the boot classpath.
     * @return This builder instance.
     */
    public JavapCLIBuilder bootClassPath(Path... bootClassPathEntries) {
        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        String[] entryStrings = Arrays.stream(bootClassPathEntries).map(Path::toString).toArray(String[]::new);
        return bootClassPath(entryStrings);
    }

    /**
     * Specifies the multi-release JAR file version. Corresponds to the {@code --multi-release} option.
     *
     * @param version The multi-release version.
     * @return This builder instance.
     */
    public JavapCLIBuilder multiRelease(String version) {
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
    public JavapCLIBuilder multiRelease(int version) {
        return multiRelease(Integer.toString(version));
    }

    /**
     * Passes options to the Java Virtual Machine. Corresponds to the {@code -J} option.
     *
     * @param options The JVM options.
     * @return This builder instance.
     */
    public JavapCLIBuilder jvmOptions(String... options) {
        Objects.requireNonNull(options, "JVM options cannot be null");
        for (String option : options) {
            Objects.requireNonNull(option, "JVM option cannot be null");
            this.arguments.add("-J" + option);
        }

        return this;
    }

    /**
     * Adds a class name to be disassembled.
     *
     * @param className The name of the class.
     * @return This builder instance.
     */
    public JavapCLIBuilder addClassName(String className) {
        Objects.requireNonNull(className, "Class name cannot be null");
        this.classTargets.add(className);
        return this;
    }

    /**
     * Adds multiple class names to be disassembled.
     *
     * @param classNames The names of the classes.
     * @return This builder instance.
     */
    public JavapCLIBuilder addClassNames(String... classNames) {
        Objects.requireNonNull(classNames, "Class names cannot be null");
        for (String className : classNames) {
            addClassName(className);
        }

        return this;
    }

    /**
     * Adds a class file to be disassembled.
     *
     * @param classFilePath The path to the class file.
     * @return This builder instance.
     */
    public JavapCLIBuilder addClassFile(Path classFilePath) {
        Objects.requireNonNull(classFilePath, "Class file path cannot be null");
        this.classTargets.add(classFilePath.toString());
        return this;
    }

    /**
     * Adds multiple class files to be disassembled.
     *
     * @param classFilePaths The paths to the class files.
     * @return This builder instance.
     */
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

    /**
     * Represents the visibility levels for members to be printed.
     */
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
