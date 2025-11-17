package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A fluent builder for creating and executing {@code java} commands.
 * <p>
 * This comprehensive builder supports a wide array of standard, extended (-X), and advanced (-XX)
 * options for the Java launcher. It allows for detailed configuration of the classpath, module path,
 * garbage collection, memory management, JIT compilation, and much more.
 * <p>
 * Methods in this builder correspond to specific command-line flags. Version-specific options
 * will throw an {@link UnsupportedOperationException} if used with an incompatible JDK version.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html">java command documentation</a>
 */
// NOTE: Some options are version-specific; ensure compatibility with the selected JDK version
public class JavaExecutableCLIBuilder implements CLIBuilder<Process, JavaExecutableCLIBuilder> {
    private final JDK jdk;
    private final String primaryArgument;
    private final List<String> arguments = new ArrayList<>();
    private Path workingDirectory;
    private final Map<String, String> environmentVariables = new HashMap<>();
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;
    private boolean enableConsole = false;
    private final LaunchType launchType;

    private JavaExecutableCLIBuilder(LaunchType launchType, JDK jdk, String primaryArgument) {
        this.launchType = Objects.requireNonNull(launchType, "Launch type cannot be null");
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
        this.primaryArgument = Objects.requireNonNull(primaryArgument, "Primary argument cannot be null");
    }

    /**
     * Creates a builder to launch a {@code .class} file.
     *
     * @param jdk           The JDK to use.
     * @param classFilePath The path to the class file.
     * @return A new builder instance.
     */
    public static JavaExecutableCLIBuilder classFile(JDK jdk, Path classFilePath) {
        Objects.requireNonNull(classFilePath, "Class file path cannot be null");
        if (!classFilePath.toString().endsWith(".class"))
            throw new IllegalArgumentException("Provided path is not a .class file: " + classFilePath);
        return new JavaExecutableCLIBuilder(LaunchType.CLASS_FILE, jdk, classFilePath.toString());
    }

    /**
     * Creates a builder to launch an executable {@code .jar} file.
     *
     * @param jdk         The JDK to use.
     * @param jarFilePath The path to the JAR file.
     * @return A new builder instance.
     */
    public static JavaExecutableCLIBuilder jarFile(JDK jdk, Path jarFilePath) {
        Objects.requireNonNull(jarFilePath, "JAR file path cannot be null");
        if (!jarFilePath.toString().endsWith(".jar"))
            throw new IllegalArgumentException("Provided path is not a .jar file: " + jarFilePath);

        return new JavaExecutableCLIBuilder(LaunchType.JAR_FILE, jdk, jarFilePath.toString());
    }

    /**
     * Creates a builder to launch a module.
     *
     * @param jdk        The JDK to use.
     * @param moduleName The name of the module to launch.
     * @return A new builder instance.
     */
    public static JavaExecutableCLIBuilder module(JDK jdk, String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        return new JavaExecutableCLIBuilder(LaunchType.MODULE, jdk, moduleName);
    }

    /**
     * Creates a builder to launch a single-file {@code .java} source program.
     *
     * @param jdk            The JDK to use.
     * @param sourceFilePath The path to the source file.
     * @return A new builder instance.
     */
    public static JavaExecutableCLIBuilder sourceFile(JDK jdk, Path sourceFilePath) {
        Objects.requireNonNull(sourceFilePath, "Source file path cannot be null");
        if (!sourceFilePath.toString().endsWith(".java"))
            throw new IllegalArgumentException("Provided path is not a .java file: " + sourceFilePath);

        return new JavaExecutableCLIBuilder(LaunchType.SOURCE_FILE, jdk, sourceFilePath.toString());
    }

    @Override
    public JavaExecutableCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JavaExecutableCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JavaExecutableCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JavaExecutableCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JavaExecutableCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * On Windows, specifies whether to use {@code java.exe} (with a console) or {@code javaw.exe} (without a console).
     * This setting has no effect on other operating systems.
     *
     * @param enableConsole {@code true} to use {@code java.exe}, {@code false} to use {@code javaw.exe}.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableConsole(boolean enableConsole) {
        this.enableConsole = enableConsole;
        return this;
    }

    /**
     * Loads a native agent library (e.g., for profiling or debugging). Corresponds to the {@code -agentlib} flag.
     *
     * @param agentLib The name of the agent library.
     * @param options  Optional arguments for the agent.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder agentlib(String agentLib, String... options) {
        Objects.requireNonNull(agentLib, "Agent library cannot be null");
        Objects.requireNonNull(options, "Options array cannot be null");
        var agentArgument = new StringBuilder("-agentlib:").append(agentLib);
        if (options.length > 0) {
            agentArgument.append("=");
            agentArgument.append(String.join(",", options));
        }

        this.arguments.addFirst(agentArgument.toString());
        return this;
    }

    /**
     * Loads a native agent library by its full path. Corresponds to the {@code -agentpath} flag.
     *
     * @param agentPath The path to the agent library.
     * @param options   Optional arguments for the agent.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder agentpath(Path agentPath, String... options) {
        Objects.requireNonNull(agentPath, "Agent path cannot be null");
        Objects.requireNonNull(options, "Options array cannot be null");
        var agentArgument = new StringBuilder("-agentpath:").append(agentPath);
        if (options.length > 0) {
            agentArgument.append("=");
            agentArgument.append(String.join(",", options));
        }

        this.arguments.add(agentArgument.toString());
        return this;
    }

    /**
     * Sets the class search path. Corresponds to the {@code -cp} or {@code -classpath} flag.
     *
     * @param classpathEntries The entries for the classpath.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder classpath(String... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        this.arguments.add("-cp " + String.join(File.pathSeparator, classpathEntries));
        return this;
    }

    /**
     * Disables the use of argument files ({@code @files}).
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableAtFiles() {
        this.arguments.add("--disable-@files");
        return this;
    }

    /**
     * Enables preview language features. Corresponds to the {@code --enable-preview} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if the JDK version is less than 12.
     */
    public JavaExecutableCLIBuilder enablePreviewFeatures() {
        if (jdk.version().major() < 12)
            throw new UnsupportedOperationException("Preview features are only supported in JDK 12 and above.");

        this.arguments.add("--enable-preview");
        return this;
    }

    /**
     * Enables native access for a specific module. Corresponds to the {@code --enable-native-access} flag.
     *
     * @param moduleName The name of the module to grant native access to.
     * @return This builder instance.
     * @throws UnsupportedOperationException if the JDK version is less than 16.
     */
    public JavaExecutableCLIBuilder enableNativeAccess(String moduleName) {
        if (jdk.version().major() < 16)
            throw new UnsupportedOperationException("Enabling native access is only supported in JDK 16 and above.");

        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--enable-native-access=" + moduleName);
        return this;
    }

    /**
     * Enables native access for all unnamed modules.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableNativeAccess() {
        return enableNativeAccess("ALL-UNNAMED");
    }

    /**
     * Sets the behavior for illegal native access. Corresponds to the {@code --illegal-native-access} flag.
     *
     * @param mode The access mode to set.
     * @return This builder instance.
     * @deprecated As of Java 25, this flag is for removal.
     */
    @Deprecated(forRemoval = true, since = "Java 25")
    public JavaExecutableCLIBuilder illegalNativeAccess(AccessMode mode) {
        if (jdk.version().major() < 24)
            throw new UnsupportedOperationException("Setting illegal native access mode is only supported in JDK 24 and above.");
        if (mode == AccessMode.DEBUG)
            throw new UnsupportedOperationException("DEBUG mode is not available for illegal native access.");

        Objects.requireNonNull(mode, "Native access mode cannot be null");
        this.arguments.add("--illegal-native-access=" + mode.getMode());
        return this;
    }

    /**
     * Enables or disables finalization. Corresponds to the {@code --finalization} flag.
     *
     * @param state The desired state of finalization.
     * @return This builder instance.
     * @throws UnsupportedOperationException if the JDK version is less than 18.
     */
    public JavaExecutableCLIBuilder finalization(EnabledDisabled state) {
        if (jdk.version().major() < 18)
            throw new UnsupportedOperationException("Controlling finalization is only supported in JDK 18 and above.");

        Objects.requireNonNull(state, "Finalization state cannot be null");
        this.arguments.add("--finalization=" + state.getState());
        return this;
    }

    /**
     * Sets the module path. Corresponds to the {@code --module-path} or {@code -p} flag.
     *
     * @param modulePaths The entries for the module path.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Sets the upgrade module path. Corresponds to the {@code --upgrade-module-path} flag.
     *
     * @param upgradeModulePaths The entries for the upgrade module path.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder upgradeModulePath(String... upgradeModulePaths) {
        Objects.requireNonNull(upgradeModulePaths, "Upgrade module path entries cannot be null");
        this.arguments.add("--upgrade-module-path " + String.join(File.pathSeparator, upgradeModulePaths));
        return this;
    }

    /**
     * Adds a set of root modules to the initial resolution. Corresponds to the {@code --add-modules} flag.
     *
     * @param modules The names of the modules to add.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Adds a predefined set of root modules (e.g., all system modules).
     *
     * @param rootModule The predefined set of modules to add.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addModules(RootModule rootModule) {
        Objects.requireNonNull(rootModule, "Root module cannot be null");
        this.arguments.add("--add-modules " + rootModule.getModule());
        return this;
    }

    /**
     * Lists the observable modules and exits. Corresponds to the {@code --list-modules} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder listModules() {
        this.arguments.add("--list-modules");
        return this;
    }

    /**
     * Describes a given module and exits. Corresponds to the {@code --describe-module} flag.
     *
     * @param moduleName The name of the module to describe.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder describeModule(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--describe-module " + moduleName);
        return this;
    }

    /**
     * Performs a dry run of the module system and exits. Corresponds to the {@code --dry-run} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder dryRun() {
        this.arguments.add("--dry-run");
        return this;
    }

    /**
     * Validates the module graph and exits. Corresponds to the {@code --validate-modules} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder validateModules() {
        this.arguments.add("--validate-modules");
        return this;
    }

    /**
     * Sets a system property. Corresponds to the {@code -Dkey=value} flag.
     *
     * @param key   The property key.
     * @param value The property value.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder systemProperty(String key, String value) {
        Objects.requireNonNull(key, "System property key cannot be null");
        Objects.requireNonNull(value, "System property value cannot be null");
        String normalizedValue = value.startsWith("\"") && value.endsWith("\"") ? value : "\"" + value + "\"";
        this.arguments.add("-D" + key + "=" + normalizedValue);
        return this;
    }

    /**
     * Disables assertions for all classes. Corresponds to the {@code -da} or {@code -disableassertions} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableAssertions() {
        this.arguments.add("-da");
        return this;
    }

    /**
     * Disables assertions for a specific package or class.
     *
     * @param packageOrClassName The name of the package or class.
     * @param subpackages        If {@code true}, also applies to subpackages.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableAssertions(String packageOrClassName, boolean subpackages) {
        Objects.requireNonNull(packageOrClassName, "Package or class name cannot be null");
        this.arguments.add("-da:" + packageOrClassName + (subpackages ? "..." : ""));
        return this;
    }

    /**
     * Disables assertions for a specific package or class.
     *
     * @param packageOrClassName The name of the package or class.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableAssertions(String packageOrClassName) {
        return disableAssertions(packageOrClassName, false);
    }

    /**
     * Disables assertions in all system classes. Corresponds to the {@code -dsa} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableSystemAssertions() {
        this.arguments.add("-dsa");
        return this;
    }

    /**
     * Enables assertions for all classes. Corresponds to the {@code -ea} or {@code -enableassertions} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAssertions() {
        this.arguments.add("-ea");
        return this;
    }

    /**
     * Enables assertions for a specific package or class.
     *
     * @param packageOrClassName The name of the package or class.
     * @param subpackages        If {@code true}, also applies to subpackages.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAssertions(String packageOrClassName, boolean subpackages) {
        Objects.requireNonNull(packageOrClassName, "Package or class name cannot be null");
        this.arguments.add("-ea:" + packageOrClassName + (subpackages ? "..." : ""));
        return this;
    }

    /**
     * Enables assertions for a specific package or class.
     *
     * @param packageOrClassName The name of the package or class.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAssertions(String packageOrClassName) {
        return enableAssertions(packageOrClassName, false);
    }

    /**
     * Enables assertions in all system classes. Corresponds to the {@code -esa} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSystemAssertions() {
        this.arguments.add("-esa");
        return this;
    }

    /**
     * Prints the help message. Corresponds to the {@code -help} or {@code --help} flag.
     *
     * @param errorOutput If {@code true}, prints to standard error.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder help(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-help" : "--help");
        return this;
    }

    /**
     * Loads a Java programming language agent. Corresponds to the {@code -javaagent} flag.
     *
     * @param javaAgentPath The path to the agent JAR file.
     * @param options       Optional arguments for the agent.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder javaagent(String javaAgentPath, String... options) {
        Objects.requireNonNull(javaAgentPath, "Java agent path cannot be null");
        Objects.requireNonNull(options, "Options array cannot be null");
        var agentArgument = new StringBuilder("-javaagent:").append(javaAgentPath);
        if (options.length > 0) {
            agentArgument.append("=");
            agentArgument.append(String.join(",", options));
        }

        this.arguments.addFirst(agentArgument.toString());
        return this;
    }

    /**
     * Loads a Java programming language agent. Corresponds to the {@code -javaagent} flag.
     *
     * @param javaAgentPath The path to the agent JAR file.
     * @param options       Optional arguments for the agent.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder javaagent(Path javaAgentPath, String... options) {
        Objects.requireNonNull(javaAgentPath, "Java agent path cannot be null");
        return javaagent(javaAgentPath.toString(), options);
    }

    /**
     * Shows version information and continues execution. Corresponds to the {@code -showversion} or {@code --showversion} flag.
     *
     * @param errorOutput If {@code true}, prints to standard error.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder showVersion(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-showversion" : "--showversion");
        return this;
    }

    /**
     * Shows the module resolution output. Corresponds to the {@code --show-module-resolution} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder showModuleResolution() {
        this.arguments.add("--show-module-resolution");
        return this;
    }

    /**
     * Shows the splash screen with a specified image. Corresponds to the {@code --splash} flag.
     *
     * @param splashImagePath The path to the splash screen image.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder splashScreen(String splashImagePath) {
        Objects.requireNonNull(splashImagePath, "Splash image path cannot be null");
        this.arguments.add("--splash:" + splashImagePath);
        return this;
    }

    /**
     * Shows the splash screen with a specified image. Corresponds to the {@code --splash} flag.
     *
     * @param splashImagePath The path to the splash screen image.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder splashScreen(Path splashImagePath) {
        Objects.requireNonNull(splashImagePath, "Splash image path cannot be null");
        return splashScreen(splashImagePath.toString());
    }

    /**
     * Enables verbose output for a specific component. Corresponds to the {@code -verbose} flag.
     *
     * @param component The component to make verbose (e.g., gc, class, jni).
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder verbose(VerboseComponent component) {
        Objects.requireNonNull(component, "Verbose component cannot be null");
        this.arguments.add("-verbose:" + component.getComponent());
        return this;
    }

    /**
     * Prints version information and exits. Corresponds to the {@code -version} or {@code --version} flag.
     *
     * @param errorOutput If {@code true}, prints to standard error.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder version(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-version" : "--version");
        return this;
    }

    /**
     * Shows help on extra non-standard options. Corresponds to the {@code -X} or {@code --help-extra} flag.
     *
     * @param errorOutput If {@code true}, prints to standard error.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder extraOptionsHelp(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-X" : "--help-extra");
        return this;
    }

    /**
     * Reads additional arguments from a file.
     *
     * @param argFilePath The path to the argument file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addArgFile(Path argFilePath) {
        Objects.requireNonNull(argFilePath, "Argument file path cannot be null");
        this.arguments.add("@" + argFilePath);
        return this;
    }

    /**
     * Disables background compilation. Corresponds to the {@code -Xbatch} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableBackgroundCompilation() {
        this.arguments.add("-Xbatch");
        return this;
    }

    /**
     * Appends entries to the bootstrap class path. Corresponds to the {@code -Xbootclasspath/a:} flag.
     *
     * @param bootClassPathEntries The entries to append.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder appendBootClassPath(String... bootClassPathEntries) {
        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        this.arguments.add("-Xbootclasspath/a:" + String.join(File.pathSeparator, bootClassPathEntries));
        return this;
    }

    /**
     * Performs additional, more rigorous JNI checks. Corresponds to the {@code -Xcheck:jni} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder performAdditionalJNIChecks() {
        this.arguments.add("-Xcheck:jni");
        return this;
    }

    /**
     * Forces compilation of all methods on first invocation. Corresponds to the {@code -Xcomp} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder exerciseJITCompiler() {
        this.arguments.add("-Xcomp");
        return this;
    }

    /**
     * Enables debugging support.
     *
     * @return This builder instance.
     * @deprecated As of Java 5, use {@code -agentlib:jdwp} instead.
     */
    @Deprecated(forRemoval = true, since = "Java 5")
    public JavaExecutableCLIBuilder enableDebuggingSupport() {
        this.arguments.add("-Xdebug");
        return this;
    }

    /**
     * Enables additional diagnostic messages. Corresponds to the {@code -Xdiag} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder additionalDiagnosticMessages() {
        this.arguments.add("-Xdiag");
        return this;
    }

    /**
     * Runs the application in interpreted-only mode. Corresponds to the {@code -Xint} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder interpretOnlyMode() {
        this.arguments.add("-Xint");
        return this;
    }

    /**
     * Prints internal version information. Corresponds to the {@code -Xinternalversion} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder internalVersionInfo() {
        this.arguments.add("-Xinternalversion");
        return this;
    }

    /**
     * Configures or disables logging. Corresponds to the {@code -Xlog} flag.
     *
     * @param loggingOptions The raw logging configuration string.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder configureLogging(String loggingOptions) {
        Objects.requireNonNull(loggingOptions, "Logging options cannot be null");
        this.arguments.add("-Xlog:" + loggingOptions);
        return this;
    }

    /**
     * Configures logging using a fluent builder.
     *
     * @param configuration The logging configuration object.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder configureLogging(LoggingConfiguration configuration) {
        Objects.requireNonNull(configuration, "Logging configuration cannot be null");
        return configureLogging(configuration.asArgument());
    }

    /**
     * Creates a new logging configuration builder.
     *
     * @return A new {@link LoggingConfiguration} instance.
     */
    public static LoggingConfiguration loggingConfiguration() {
        return new LoggingConfiguration();
    }

    /**
     * Runs the application in mixed mode (compiled and interpreted). Corresponds to the {@code -Xmixed} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder mixedMode() {
        this.arguments.add("-Xmixed");
        return this;
    }

    /**
     * Sets the size of the young generation heap. Corresponds to the {@code -Xmn} flag.
     *
     * @param size The size.
     * @param unit The unit of size (e.g., MEGABYTES).
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder generationalMaxHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size cannot be null");
        this.arguments.add("-Xmn" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the initial heap size. Corresponds to the {@code -Xms} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder minimumHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size cannot be null");
        this.arguments.add("-Xms" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum heap size. Corresponds to the {@code -Xmx} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maximumHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size cannot be null");
        this.arguments.add("-Xmx" + size + unit.getUnit());
        return this;
    }

    /**
     * Disables garbage collection of classes. Corresponds to the {@code -Xnoclassgc} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableClassGC() {
        this.arguments.add("-Xnoclassgc");
        return this;
    }

    /**
     * Reduces the use of OS signals by the JVM. Corresponds to the {@code -Xrs} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder reduceSignalUsage() {
        this.arguments.add("-Xrs");
        return this;
    }

    /**
     * Sets the class data sharing mode. Corresponds to the {@code -Xshare} flag.
     *
     * @param mode The desired mode (auto, on, off).
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder setClassDataSharingMode(AutoOnOff mode) {
        Objects.requireNonNull(mode, "Class Data Sharing mode cannot be null");
        this.arguments.add("-Xshare:class" + mode.getMode());
        return this;
    }

    /**
     * Shows all JVM settings and exits. Corresponds to the {@code -XshowSettings} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder showSettings() {
        this.arguments.add("-XshowSettings");
        return this;
    }

    /**
     * Shows settings for a specific category and exits.
     *
     * @param category The category of settings to show.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder showSettings(SettingCategory category) {
        Objects.requireNonNull(category, "Setting category cannot be null");
        if (category == SettingCategory.SYSTEM && !OperatingSystem.isLinux())
            throw new UnsupportedOperationException("Showing system settings is only supported on Linux systems.");

        this.arguments.add("-XshowSettings:" + category.getCategoryName());
        return this;
    }

    /**
     * Sets the thread stack size. Corresponds to the {@code -Xss} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder threadStackSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Stack size unit cannot be null");
        this.arguments.add("-Xss" + size + unit.getUnit());
        return this;
    }

    /**
     * Adds a read edge from a source module to target modules. Corresponds to the {@code --add-reads} flag.
     *
     * @param sourceModule  The source module.
     * @param targetModules The target modules.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addReads(String sourceModule, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-reads " + sourceModule + "=" + String.join(",", targetModules));
        return this;
    }

    /**
     * Adds a read edge from a source module to all unnamed modules.
     *
     * @param sourceModule The source module.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addReadsAllUnnamed(String sourceModule) {
        return addReads(sourceModule, "ALL-UNNAMED");
    }

    /**
     * Adds an export of a package from a source module to target modules. Corresponds to the {@code --add-exports} flag.
     *
     * @param sourceModule  The source module.
     * @param packageName   The package to export.
     * @param targetModules The target modules.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addExports(String sourceModule, String packageName, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-exports " + sourceModule + "/" + packageName + "=" + String.join(",", targetModules));
        return this;
    }

    /**
     * Adds an export of a package from a source module to all unnamed modules.
     *
     * @param sourceModule The source module.
     * @param packageName  The package to export.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addExportsAllUnnamed(String sourceModule, String packageName) {
        return addExports(sourceModule, packageName, "ALL-UNNAMED");
    }

    /**
     * Adds an open of a package from a source module to target modules. Corresponds to the {@code --add-opens} flag.
     *
     * @param sourceModule  The source module.
     * @param packageName   The package to open.
     * @param targetModules The target modules.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addOpens(String sourceModule, String packageName, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-opens " + sourceModule + "/" + packageName + "=" + String.join(",", targetModules));
        return this;
    }

    /**
     * Adds an open of a package from a source module to all unnamed modules.
     *
     * @param sourceModule The source module.
     * @param packageName  The package to open.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder addOpensAllUnnamed(String sourceModule, String packageName) {
        return addOpens(sourceModule, packageName, "ALL-UNNAMED");
    }

    /**
     * Limits the universe of observable modules. Corresponds to the {@code --limit-modules} flag.
     *
     * @param modules The modules to limit to.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder limitModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--limit-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Patches a module with classes and resources from specified paths. Corresponds to the {@code --patch-module} flag.
     *
     * @param moduleName The module to patch.
     * @param patchPaths The paths to the patch content.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder patchModule(String moduleName, String... patchPaths) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(patchPaths, "Patch paths cannot be null");
        this.arguments.add("--patch-module " + moduleName + "=" + String.join(File.pathSeparator, patchPaths));
        return this;
    }

    /**
     * Sets the source version for single-file source-code programs. Corresponds to the {@code --source} flag.
     *
     * @param version The source version.
     * @return This builder instance.
     * @throws UnsupportedOperationException if not launching a source file.
     */
    public JavaExecutableCLIBuilder sourceVersion(String version) {
        if (launchType != LaunchType.SOURCE_FILE)
            throw new UnsupportedOperationException("Setting source version is only supported when launching a source file.");

        Objects.requireNonNull(version, "Source version cannot be null");
        this.arguments.add("--source " + version);
        return this;
    }

    /**
     * Sets the access mode for {@code sun.misc.Unsafe} memory access. Corresponds to the {@code --sun-misc-unsafe-memory-access} flag.
     *
     * @param mode The access mode.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sunMiscUnsafeMemoryAccess(AccessMode mode) {
        Objects.requireNonNull(mode, "Unsafe memory access mode cannot be null");
        this.arguments.add("--sun-misc-unsafe-memory-access=" + mode.getMode());
        return this;
    }

    /**
     * Ensures the main thread is the first thread on macOS. Corresponds to the {@code -XstartOnFirstThread} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on macOS.
     */
    public JavaExecutableCLIBuilder startOnFirstThread() {
        if (!OperatingSystem.isMac())
            throw new UnsupportedOperationException("Starting on first thread is only supported on macOS.");

        this.arguments.add("-XstartOnFirstThread");
        return this;
    }

    /**
     * Sets the application name in the macOS dock. Corresponds to the {@code -Xdock:name} flag.
     *
     * @param appName The application name.
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on macOS.
     */
    public JavaExecutableCLIBuilder dockName(String appName) {
        if (!OperatingSystem.isMac())
            throw new UnsupportedOperationException("Setting dock name is only supported on macOS.");

        Objects.requireNonNull(appName, "Application name cannot be null");
        this.arguments.add("-Xdock:name=" + appName);
        return this;
    }

    /**
     * Sets the application icon in the macOS dock. Corresponds to the {@code -Xdock:icon} flag.
     *
     * @param iconPath The path to the icon file.
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on macOS.
     */
    public JavaExecutableCLIBuilder dockIcon(Path iconPath) {
        if (!OperatingSystem.isMac())
            throw new UnsupportedOperationException("Setting dock icon is only supported on macOS.");

        Objects.requireNonNull(iconPath, "Icon path cannot be null");
        this.arguments.add("-Xdock:icon=" + iconPath);
        return this;
    }

    /**
     * Unlocks diagnostic VM options. Corresponds to the {@code -XX:+UnlockDiagnosticVMOptions} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder unlockDiagnosticVMOptions() {
        this.arguments.add("-XX:+UnlockDiagnosticVMOptions");
        return this;
    }

    /**
     * Unlocks experimental VM options. Corresponds to the {@code -XX:+UnlockExperimentalVMOptions} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder unlockExperimentalVMOptions() {
        this.arguments.add("-XX:+UnlockExperimentalVMOptions");
        return this;
    }

    /**
     * Sets the number of active processors for the VM to use. Corresponds to the {@code -XX:ActiveProcessorCount} flag.
     *
     * @param count The number of processors.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder activeProcessorCount(int count) {
        if (count <= 0)
            throw new IllegalArgumentException("Active processor count must be positive.");

        this.arguments.add("-XX:ActiveProcessorCount=" + count);
        return this;
    }

    /**
     * Specifies a path for NUMA interleaving memory allocation. Corresponds to the {@code -XX:AllocateHeapAt} flag.
     *
     * @param path The allocation path.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocateHeapAt(Path path) {
        Objects.requireNonNull(path, "Heap allocation path cannot be null");
        this.arguments.add("-XX:AllocateHeapAt=" + path);
        return this;
    }

    /**
     * Disables the use of compact strings. Corresponds to the {@code -XX:-CompactStrings} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableCompactStrings() {
        this.arguments.add("-XX:-CompactStrings");
        return this;
    }

    /**
     * Sets the path for writing fatal error logs. Corresponds to the {@code -XX:ErrorFile} flag.
     *
     * @param errorFilePath The path to the error file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder errorFile(Path errorFilePath) {
        Objects.requireNonNull(errorFilePath, "Error file path cannot be null");
        this.arguments.add("-XX:ErrorFile=" + errorFilePath);
        return this;
    }

    /**
     * Enables extensive error reports. Corresponds to the {@code -XX:+ExtensiveErrorReports} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableExtensiveErrorReports() {
        this.arguments.add("-XX:+ExtensiveErrorReports");
        return this;
    }

    /**
     * Configures Java Flight Recorder (JFR) options. Corresponds to the {@code -XX:FlightRecorderOptions} flag.
     *
     * @param options The JFR options to set.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder flightRecorderOptions(FlightRecorderOption... options) {
        Objects.requireNonNull(options, "Flight recorder options cannot be null");
        List<String> optionStrings = new ArrayList<>();
        for (FlightRecorderOption option : options) {
            optionStrings.add(option.name() + "=" + option.value());
        }

        this.arguments.add("-XX:FlightRecorderOptions:" + String.join(",", optionStrings));
        return this;
    }

    /**
     * Sets the large page size. Corresponds to the {@code -XX:LargePageSizeInBytes} flag.
     *
     * @param size The page size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder largePageSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Page size unit cannot be null");
        this.arguments.add("-XX:LargePageSizeInBytes=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum size for direct memory allocation. Corresponds to the {@code -XX:MaxDirectMemorySize} flag.
     *
     * @param size The memory size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxDirectMemorySize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Direct memory size unit cannot be null");
        this.arguments.add("-XX:MaxDirectMemorySize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Disables the limit on the number of file descriptors. Corresponds to the {@code -XX:-MaxFDLimit} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableMaxFileDescriptorLimit() {
        this.arguments.add("-XX:-MaxFDLimit");
        return this;
    }

    /**
     * Configures native memory tracking. Corresponds to the {@code -XX:NativeMemoryTracking} flag.
     *
     * @param tracking The tracking mode.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder nativeMemoryTracking(NativeMemoryTracking tracking) {
        Objects.requireNonNull(tracking, "Native memory tracking mode cannot be null");
        this.arguments.add("-XX:NativeMemoryTracking=" + tracking.getState());
        return this;
    }

    /**
     * Sets the interval for trimming the native heap on Linux. Corresponds to the {@code -XX:TrimNativeHeapInterval} flag.
     *
     * @param interval The interval duration.
     * @param unit     The time unit of the interval.
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on Linux.
     */
    public JavaExecutableCLIBuilder trimNativeHeapInterval(long interval, TimeUnit unit) {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("TrimNativeHeapInterval is only supported on Linux systems.");

        if (interval < 0)
            throw new IllegalArgumentException("Trim interval cannot be negative.");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.arguments.add("-XX:TrimNativeHeapInterval=" + unit.toMillis(interval));
        return this;
    }

    /**
     * Emulates the client VM. Corresponds to the {@code -XX:+NeverActAsServerClassMachine} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableClientVMEmulation() {
        this.arguments.add("-XX:+NeverActAsServerClassMachine");
        return this;
    }

    /**
     * Sets the object alignment in bytes. Corresponds to the {@code -XX:ObjectAlignmentInBytes} flag.
     *
     * @param alignment The alignment value (must be a power of two between 8 and 256).
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder objectAlignmentInBytes(int alignment) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0)
            throw new IllegalArgumentException("Object alignment must be a positive power of two.");

        if (alignment < 8 || alignment > 256)
            throw new IllegalArgumentException("Object alignment must be between 8 and 256 bytes.");

        this.arguments.add("-XX:ObjectAlignmentInBytes=" + alignment);
        return this;
    }

    /**
     * Specifies a command to run on a fatal error. Corresponds to the {@code -XX:OnError} flag.
     *
     * @param command The command to execute.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder onError(String command) {
        Objects.requireNonNull(command, "OnError command cannot be null");
        this.arguments.add("-XX:OnError=\"" + command + "\"");
        return this;
    }

    /**
     * Specifies a command to run on an OutOfMemoryError. Corresponds to the {@code -XX:OnOutOfMemoryError} flag.
     *
     * @param command The command to execute.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder onOutOfMemoryError(String command) {
        Objects.requireNonNull(command, "OnOutOfMemoryError command cannot be null");
        this.arguments.add("-XX:OnOutOfMemoryError=\"" + command + "\"");
        return this;
    }

    /**
     * Enables printing of command-line flags. Corresponds to the {@code -XX:+PrintCommandLineFlags} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enablePrintingCommandLineFlags() {
        this.arguments.add("-XX:+PrintCommandLineFlags");
        return this;
    }

    /**
     * Enables or disables preserving the frame pointer. Corresponds to the {@code -XX:+/-PreserveFramePointer} flag.
     *
     * @param preserve {@code true} to preserve, {@code false} to omit.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder preserveFramePointer(boolean preserve) {
        this.arguments.add(preserve ? "-XX:+PreserveFramePointer" : "-XX:-PreserveFramePointer");
        return this;
    }

    /**
     * Enables printing of native memory tracking statistics. Corresponds to the {@code -XX:+PrintNMTStatistics} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enablePrintingNMTStatistics() {
        this.arguments.add("-XX:+PrintNMTStatistics");
        return this;
    }

    /**
     * Specifies the path to the shared archive file. Corresponds to the {@code -XX:SharedArchiveFile} flag.
     *
     * @param archivePath The path to the archive.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sharedArchiveFile(Path archivePath) {
        Objects.requireNonNull(archivePath, "Shared archive file path cannot be null");
        this.arguments.add("-XX:SharedArchiveFile=" + archivePath);
        return this;
    }

    /**
     * Specifies the path to the dynamic shared archive file.
     *
     * @param dynamicArchivePath The path to the dynamic archive.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sharedArchiveFileDynamic(Path dynamicArchivePath) {
        Objects.requireNonNull(dynamicArchivePath, "Dynamic shared archive file path cannot be null");
        this.arguments.add("-XX:SharedArchiveFile=," + dynamicArchivePath);
        return this;
    }

    /**
     * Specifies paths to both static and dynamic shared archive files.
     *
     * @param staticArchivePath  The path to the static archive.
     * @param dynamicArchivePath The path to the dynamic archive.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sharedArchiveFile(Path staticArchivePath, Path dynamicArchivePath) {
        Objects.requireNonNull(staticArchivePath, "Static shared archive file path cannot be null");
        Objects.requireNonNull(dynamicArchivePath, "Dynamic shared archive file path cannot be null");
        this.arguments.add("-XX:SharedArchiveFile=" + staticArchivePath + File.pathSeparator + dynamicArchivePath);
        return this;
    }

    /**
     * Verifies shared spaces. Corresponds to the {@code -XX:+VerifySharedSpaces} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder verifySharedSpaces() {
        this.arguments.add("-XX:+VerifySharedSpaces");
        return this;
    }

    /**
     * Specifies the shared archive configuration file. Corresponds to the {@code -XX:SharedArchiveConfigFile} flag.
     *
     * @param configFilePath The path to the config file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sharedArchiveConfigFile(Path configFilePath) {
        Objects.requireNonNull(configFilePath, "Shared archive config file path cannot be null");
        this.arguments.add("-XX:SharedArchiveConfigFile=" + configFilePath);
        return this;
    }

    /**
     * Specifies the shared class list file for creating a CDS archive. Corresponds to the {@code -XX:SharedClassListFile} flag.
     *
     * @param classListFilePath The path to the class list file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sharedClassListFile(Path classListFilePath) {
        Objects.requireNonNull(classListFilePath, "Shared class list file path cannot be null");
        this.arguments.add("-XX:SharedClassListFile=" + classListFilePath);
        return this;
    }

    /**
     * Shows detailed code information in exception messages. Corresponds to the {@code -XX:+ShowCodeDetailsInExceptionMessages} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder showCodeDetailsInExceptionMessages() {
        this.arguments.add("-XX:+ShowCodeDetailsInExceptionMessages");
        return this;
    }

    /**
     * Shows a message box on a fatal error. Corresponds to the {@code -XX:+ShowMessageBoxOnError} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder showMessageBoxOnError() {
        this.arguments.add("-XX:+ShowMessageBoxOnError");
        return this;
    }

    /**
     * Starts a Java Flight Recorder (JFR) recording. Corresponds to the {@code -XX:StartFlightRecording} flag.
     *
     * @param parameters The JFR parameters.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder startFlightRecording(FlightRecorderParameters parameters) {
        Objects.requireNonNull(parameters, "Flight recorder parameters cannot be null");
        this.arguments.add("-XX:StartFlightRecording=" + parameters);
        return this;
    }

    /**
     * Sets the thread stack size in kilobytes. Corresponds to the {@code -Xss} flag.
     *
     * @param sizeInKB The stack size in kilobytes.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder threadStackSize(int sizeInKB) {
        if (sizeInKB <= 0)
            throw new IllegalArgumentException("Stack size must be positive.");

        this.arguments.add("-Xss" + sizeInKB + "K");
        return this;
    }

    /**
     * Uses compact object headers. Corresponds to the {@code -XX:+UseCompactObjectHeaders} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if the JDK version is less than 25.
     */
    public JavaExecutableCLIBuilder useCompactObjectHeaders() {
        if (jdk.version().major() < 25)
            throw new UnsupportedOperationException("Compact object headers are only supported in JDK 25 and above.");

        this.arguments.add("-XX:+UseCompactObjectHeaders");
        return this;
    }

    /**
     * Disables compressed object pointers. Corresponds to the {@code -XX:-UseCompressedOops} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableCompressedPointers() {
        this.arguments.add("-XX:-UseCompressedOops");
        return this;
    }

    /**
     * Disables container support on Linux. Corresponds to the {@code -XX:-UseContainerSupport} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on Linux.
     */
    public JavaExecutableCLIBuilder disableContainerSupport() {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("Disabling container support is only supported on Linux systems.");

        this.arguments.add("-XX:-UseContainerSupport");
        return this;
    }

    /**
     * Enables the use of large pages for memory allocation. Corresponds to the {@code -XX:+UseLargePages} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder useLargePages() {
        this.arguments.add("-XX:+UseLargePages");
        return this;
    }

    /**
     * Enables the use of transparent huge pages on Linux. Corresponds to the {@code -XX:+UseTransparentHugePages} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on Linux.
     */
    public JavaExecutableCLIBuilder useTransparentHugePages() {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("UseTransparentHugePages is only supported on Linux systems.");

        this.arguments.add("-XX:+UseTransparentHugePages");
        return this;
    }

    /**
     * Allows user-defined signal handlers. Corresponds to the {@code -XX:+AllowUserSignalHandlers} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if on Windows.
     */
    public JavaExecutableCLIBuilder allowInstallingSignalHandlers() {
        if (OperatingSystem.isWindows())
            throw new UnsupportedOperationException("Installing signal handlers is not supported on Windows systems.");

        this.arguments.add("-XX:+AllowUserSignalHandlers");
        return this;
    }

    /**
     * Specifies a file from which to load VM options. Corresponds to the {@code -XX:VMOptionsFile} flag.
     *
     * @param vmOptionsFilePath The path to the VM options file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder vmOptionsFile(Path vmOptionsFilePath) {
        Objects.requireNonNull(vmOptionsFilePath, "VM options file path cannot be null");
        this.arguments.add("-XX:VMOptionsFile=" + vmOptionsFilePath);
        return this;
    }

    /**
     * Sets the branch protection mode on AArch64 Linux. Corresponds to the {@code -XX:BranchProtection} flag.
     *
     * @param mode The branch protection mode.
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on AArch64 Linux.
     */
    public JavaExecutableCLIBuilder branchProtectionMode(BranchProtectionMode mode) {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("Branch protection mode is only supported on Linux (AArch64) systems.");

        Objects.requireNonNull(mode, "Branch protection mode cannot be null");
        this.arguments.add("-XX:BranchProtection=" + mode.getMode());
        return this;
    }

    /**
     * Sets the number of cache lines to prefetch on instance allocation. Corresponds to the {@code -XX:AllocateInstancePrefetchLines} flag.
     *
     * @param lineCount The number of lines.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocateInstancePrefetchLines(int lineCount) {
        if (lineCount < 0)
            throw new IllegalArgumentException("Line count cannot be negative.");

        this.arguments.add("-XX:AllocateInstancePrefetchLines=" + lineCount);
        return this;
    }

    /**
     * Sets the prefetch distance for object allocation. Corresponds to the {@code -XX:AllocatePrefetchDistance} flag.
     *
     * @param distance The distance.
     * @param unit     The unit of distance.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocatePrefetchDistance(long distance, ByteUnit unit) {
        Objects.requireNonNull(unit, "Prefetch distance unit cannot be null");
        if (distance < -1)
            throw new IllegalArgumentException("Prefetch distance cannot be negative.");

        this.arguments.add("-XX:AllocatePrefetchDistance=" + distance + unit.getUnit());
        return this;
    }

    /**
     * Sets the prefetch instruction type for allocation. Corresponds to the {@code -XX:AllocatePrefetchInstr} flag.
     *
     * @param instructionType The instruction type (0-3).
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocatePrefetchInstruction(byte instructionType) {
        if (instructionType < 0 || instructionType > 3)
            throw new IllegalArgumentException("Instruction type must be 0, 1, 2, or 3.");

        this.arguments.add("-XX:AllocatePrefetchInstr=" + instructionType);
        return this;
    }

    /**
     * Sets the number of cache lines to prefetch ahead of the current allocation pointer. Corresponds to the {@code -XX:AllocatePrefetchLines} flag.
     *
     * @param lineCount The number of lines.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocatePrefetchLines(int lineCount) {
        if (lineCount < 0)
            throw new IllegalArgumentException("Line count cannot be negative.");

        this.arguments.add("-XX:AllocatePrefetchLines=" + lineCount);
        return this;
    }

    /**
     * Sets the prefetch step size. Corresponds to the {@code -XX:AllocatePrefetchStepSize} flag.
     *
     * @param stepSize The step size.
     * @param unit     The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocatePrefetchStepSize(int stepSize, ByteUnit unit) {
        Objects.requireNonNull(unit, "Step size unit cannot be null");
        if (stepSize < 0)
            throw new IllegalArgumentException("Step size cannot be negative.");

        this.arguments.add("-XX:AllocatePrefetchStepSize=" + stepSize + unit.getUnit());
        return this;
    }

    /**
     * Sets the prefetch style for allocation. Corresponds to the {@code -XX:AllocatePrefetchStyle} flag.
     *
     * @param style The prefetch style.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocatePrefetchStyle(PrefetchStyle style) {
        Objects.requireNonNull(style, "Prefetch style cannot be null");
        this.arguments.add("-XX:AllocatePrefetchStyle=" + style.asInt());
        return this;
    }

    /**
     * Enables background compilation. Corresponds to the {@code -XX:+BackgroundCompilation} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableBackgroundCompilation() {
        this.arguments.add("-XX:+BackgroundCompilation");
        return this;
    }

    /**
     * Sets the number of compiler threads for compilation. Corresponds to the {@code -XX:CompilerThreadsForCompilation} flag.
     *
     * @param threadCount The number of threads.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder compilerThreadsForCompilation(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("Thread count must be positive.");

        this.arguments.add("-XX:CompilerThreadsForCompilation=" + threadCount);
        return this;
    }

    /**
     * Enables dynamic adjustment of the number of compiler threads. Corresponds to the {@code -XX:+UseDynamicNumberOfCompilerThreads} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder useDynamicNumberOfCompilerThreads() {
        this.arguments.add("-XX:+UseDynamicNumberOfCompilerThreads");
        return this;
    }

    /**
     * Specifies a command to be executed on a method. Corresponds to the {@code -XX:CompileCommand} flag.
     *
     * @param command     The compile command (e.g., quiet, print, exclude).
     * @param methodSpecs The method specifications.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder compileCommand(CompileCommand command, String... methodSpecs) {
        Objects.requireNonNull(command, "Compile command cannot be null");
        Objects.requireNonNull(methodSpecs, "Method specifications cannot be null");

        this.arguments.add("-XX:CompileCommand=\"" + command.getCommand() + "," + String.join(",", methodSpecs) + "\"");
        return this;
    }

    /**
     * Specifies a file from which to read JIT compiler commands. Corresponds to the {@code -XX:CompileCommandFile} flag.
     *
     * @param commandFilePath The path to the command file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder compileCommandFile(Path commandFilePath) {
        Objects.requireNonNull(commandFilePath, "Command file path cannot be null");
        this.arguments.add("-XX:CompileCommandFile=" + commandFilePath);
        return this;
    }

    /**
     * Specifies a file from which to read JIT compiler directives. Corresponds to the {@code -XX:CompilerDirectivesFile} flag.
     *
     * @param directivesFilePath The path to the directives file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder compilerDirectivesFile(Path directivesFilePath) {
        Objects.requireNonNull(directivesFilePath, "Directives file path cannot be null");
        this.arguments.add("-XX:CompilerDirectivesFile=" + directivesFilePath);
        return this;
    }

    /**
     * Enables printing of compiler directives. Corresponds to the {@code -XX:+PrintCompilerDirectives} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder shouldPrintCompilerDirectives() {
        this.arguments.add("-XX:+PrintCompilerDirectives");
        return this;
    }

    /**
     * Compiles only the specified methods. Corresponds to the {@code -XX:CompileOnly} flag.
     *
     * @param methodSpecs The method specifications.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder compileOnly(String... methodSpecs) {
        Objects.requireNonNull(methodSpecs, "Method specifications cannot be null");

        this.arguments.add("-XX:CompileOnly=" + String.join(",", methodSpecs));
        return this;
    }

    /**
     * Scales the compilation threshold. Corresponds to the {@code -XX:CompileThresholdScale} flag.
     *
     * @param scale The scale factor.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder compileThresholdScale(int scale) {
        if (scale <= 0)
            throw new IllegalArgumentException("Scale must be positive.");

        this.arguments.add("-XX:CompileThresholdScale=" + scale);
        return this;
    }

    /**
     * Enables or disables escape analysis. Corresponds to the {@code -XX:+/-DoEscapeAnalysis} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableEscapeAnalysis(boolean enable) {
        this.arguments.add(enable ? "-XX:+DoEscapeAnalysis" : "-XX:-DoEscapeAnalysis");
        return this;
    }

    /**
     * Sets the initial code cache size. Corresponds to the {@code -XX:InitialCodeCacheSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder initialCodeCacheSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code cache size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Initial code cache size cannot be negative.");

        this.arguments.add("-XX:InitialCodeCacheSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Enables or disables method inlining. Corresponds to the {@code -XX:+/-Inline} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMethodInlining(boolean enable) {
        this.arguments.add(enable ? "-XX:+Inline" : "-XX:-Inline");
        return this;
    }

    /**
     * Sets the maximum size of a method to be inlined. Corresponds to the {@code -XX:InlineSmallCode} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder inlineSmallCode(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Inline small code size cannot be negative.");

        this.arguments.add("-XX:InlineSmallCode=" + size + unit.getUnit());
        return this;
    }

    /**
     * Enables logging of compilation activity to a {@code hotspot.log} file. Corresponds to the {@code -XX:+LogCompilation} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCompilationLogging() {
        this.arguments.add("-XX:+LogCompilation");
        return this;
    }

    /**
     * Sets the maximum bytecode size of a frequently executed method to be inlined. Corresponds to the {@code -XX:FreqInlineSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder hotMethodInlineSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Hot method inline size cannot be negative.");

        this.arguments.add("-XX:FreqInlineSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum bytecode size of a method to be inlined. Corresponds to the {@code -XX:MaxInlineSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxInlineSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max inline size cannot be negative.");

        this.arguments.add("-XX:MaxInlineSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum bytecode size for a C1-compiled method to be inlined. Corresponds to the {@code -XX:C1MaxInlineSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder c1MaxInlineSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("C1 max inline size cannot be negative.");

        this.arguments.add("-XX:C1MaxInlineSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum bytecode size of a trivial method to be inlined. Corresponds to the {@code -XX:MaxTrivialSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxTrivialSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max trivial size cannot be negative.");

        this.arguments.add("-XX:MaxTrivialSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum bytecode size for a trivial C1-compiled method to be inlined. Corresponds to the {@code -XX:C1MaxTrivialSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder c1MaxTrivialSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("C1 max trivial size cannot be negative.");

        this.arguments.add("-XX:C1MaxTrivialSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum number of nodes for a method to be compiled. Corresponds to the {@code -XX:MaxNodeLimit} flag.
     *
     * @param limit The node limit.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxNodeLimit(int limit) {
        if (limit < 0)
            throw new IllegalArgumentException("Max node limit cannot be negative.");

        this.arguments.add("-XX:MaxNodeLimit=" + limit);
        return this;
    }

    /**
     * Sets the size of the non-method code heap. Corresponds to the {@code -XX:NonNMethodCodeHeapSize} flag.
     *
     * @param sizeInBytes The size in bytes.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder nonmethodCodeHeapSize(long sizeInBytes) {
        if (sizeInBytes < 0)
            throw new IllegalArgumentException("Non-method code heap size cannot be negative.");

        this.arguments.add("-XX:NonNMethodCodeHeapSize=" + sizeInBytes);
        return this;
    }

    /**
     * Sets the size of the non-profiled code heap. Corresponds to the {@code -XX:NonProfiledCodeHeapSize} flag.
     *
     * @param sizeInBytes The size in bytes.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder nonprofiledCodeHeapSize(long sizeInBytes) {
        if (sizeInBytes < 0)
            throw new IllegalArgumentException("Non-profiled code heap size cannot be negative.");

        this.arguments.add("-XX:NonProfiledCodeHeapSize=" + sizeInBytes);
        return this;
    }

    /**
     * Enables or disables the optimization of string concatenation. Corresponds to the {@code -XX:+/-OptimizeStringConcat} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableOptimizingStringConcat(boolean enable) {
        this.arguments.add(enable ? "-XX:+OptimizeStringConcat" : "-XX:-OptimizeStringConcat");
        return this;
    }

    /**
     * Enables printing of assembly code for compiled methods. Corresponds to the {@code -XX:+PrintAssembly} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enablePrintingAssemblyCode() {
        this.arguments.add("-XX:+PrintAssembly");
        return this;
    }

    /**
     * Sets the size of the profiled code heap. Corresponds to the {@code -XX:ProfiledCodeHeapSize} flag.
     *
     * @param sizeInBytes The size in bytes.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder profiledCodeHeapSize(long sizeInBytes) {
        if (sizeInBytes < 0)
            throw new IllegalArgumentException("Profiled code heap size cannot be negative.");

        this.arguments.add("-XX:ProfiledCodeHeapSize=" + sizeInBytes);
        return this;
    }

    /**
     * Enables printing of method compilation information. Corresponds to the {@code -XX:+PrintCompilation} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMethodCompilationPrinting() {
        this.arguments.add("-XX:+PrintCompilation");
        return this;
    }

    /**
     * Enables printing of inlining decisions. Corresponds to the {@code -XX:+PrintInlining} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableInliningInfoPrinting() {
        this.arguments.add("-XX:+PrintInlining");
        return this;
    }

    /**
     * Sets the reserved code cache size. Corresponds to the {@code -XX:ReservedCodeCacheSize} flag.
     *
     * @param size The size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder reserveCodeCacheSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code cache size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Reserved code cache size cannot be negative.");

        if (unit.toBytes(size) > ByteUnit.GIGABYTES.toBytes(2))
            throw new IllegalArgumentException("Reserved code cache size cannot exceed 2 GB.");

        this.arguments.add("-XX:ReservedCodeCacheSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Enables the use of a segmented code cache. Corresponds to the {@code -XX:+SegmentedCodeCache} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSegmentedCodeCache() {
        this.arguments.add("-XX:+SegmentedCodeCache");
        return this;
    }

    /**
     * Sets the percentage of heap occupancy at which to start aggressive sweeping. Corresponds to the {@code -XX:AggressiveHeapSweepingAt} flag.
     *
     * @param percentage The percentage (0-100).
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder startAggressiveSweepingAt(int percentage) {
        if (percentage < 0 || percentage > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");

        this.arguments.add("-XX:AggressiveHeapSweepingAt=" + percentage);
        return this;
    }

    /**
     * Disables tiered compilation. Corresponds to the {@code -XX:-TieredCompilation} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableTieredCompilation() {
        this.arguments.add("-XX:-TieredCompilation");
        return this;
    }

    /**
     * Sets the version of the SSE instruction set to use. Corresponds to the {@code -XX:UseSSE} flag.
     *
     * @param version The SSE version.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder sseInstructionSetVersion(String version) {
        Objects.requireNonNull(version, "SSE instruction set version cannot be null");
        this.arguments.add("-XX:UseSSE=" + version);
        return this;
    }

    /**
     * Sets the version of the AVX instruction set to use. Corresponds to the {@code -XX:UseAVX} flag.
     *
     * @param version The AVX version.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder avxInstructionSetVersion(String version) {
        Objects.requireNonNull(version, "AVX instruction set version cannot be null");
        this.arguments.add("-XX:UseAVX=" + version);
        return this;
    }

    /**
     * Enables or disables hardware-based AES intrinsics. Corresponds to the {@code -XX:+/-UseAES} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAES(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAES" : "-XX:-UseAES");
        return this;
    }

    /**
     * Enables or disables AES intrinsics. Corresponds to the {@code -XX:+/-UseAESIntrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAESIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAESIntrinsics" : "-XX:-UseAESIntrinsics");
        return this;
    }

    /**
     * Enables or disables AES/CTR intrinsics. Corresponds to the {@code -XX:+/-UseAESCTRIntrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAESCTRIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAESCTRIntrinsics" : "-XX:-UseAESCTRIntrinsics");
        return this;
    }

    /**
     * Enables or disables GHASH intrinsics. Corresponds to the {@code -XX:+/-UseGHASHIntrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableGHASHIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseGHASHIntrinsics" : "-XX:-UseGHASHIntrinsics");
        return this;
    }

    /**
     * Enables or disables ChaCha20 intrinsics. Corresponds to the {@code -XX:+/-UseChaCha20Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableChaCha20Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseChaCha20Intrinsics" : "-XX:-UseChaCha20Intrinsics");
        return this;
    }

    /**
     * Enables or disables Poly1305 intrinsics. Corresponds to the {@code -XX:+/-UsePoly1305Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enablePoly1305Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UsePoly1305Intrinsics" : "-XX:-UsePoly1305Intrinsics");
        return this;
    }

    /**
     * Enables or disables BASE64 intrinsics. Corresponds to the {@code -XX:+/-UseBASE64Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableBASE64Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseBASE64Intrinsics" : "-XX:-UseBASE64Intrinsics");
        return this;
    }

    /**
     * Enables or disables Adler32 intrinsics. Corresponds to the {@code -XX:+/-UseAdler32Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAdler32Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAdler32Intrinsics" : "-XX:-UseAdler32Intrinsics");
        return this;
    }

    /**
     * Enables or disables CRC32 intrinsics. Corresponds to the {@code -XX:+/-UseCRC32Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCRC32Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCRC32Intrinsics" : "-XX:-UseCRC32Intrinsics");
        return this;
    }

    /**
     * Enables or disables CRC32C intrinsics. Corresponds to the {@code -XX:+/-UseCRC32CIntrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCRC32CIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCRC32CIntrinsics" : "-XX:-UseCRC32CIntrinsics");
        return this;
    }

    /**
     * Enables or disables hardware-based SHA crypto intrinsics. Corresponds to the {@code -XX:+/-UseSHA} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSHA(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA" : "-XX:-UseSHA");
        return this;
    }

    /**
     * Enables or disables SHA-1 intrinsics. Corresponds to the {@code -XX:+/-UseSHA1Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSHA1Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA1Intrinsics" : "-XX:-UseSHA1Intrinsics");
        return this;
    }

    /**
     * Enables or disables SHA-256 intrinsics. Corresponds to the {@code -XX:+/-UseSHA256Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSHA256Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA256Intrinsics" : "-XX:-UseSHA256Intrinsics");
        return this;
    }

    /**
     * Enables or disables SHA-512 intrinsics. Corresponds to the {@code -XX:+/-UseSHA512Intrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSHA512Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA512Intrinsics" : "-XX:-UseSHA512Intrinsics");
        return this;
    }

    /**
     * Enables or disables {@code Math.exact} intrinsics. Corresponds to the {@code -XX:+/-UseMathExactIntrinsics} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMathExactIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMathExactIntrinsics" : "-XX:-UseMathExactIntrinsics");
        return this;
    }

    /**
     * Enables or disables the {@code multiplyToLen} intrinsic. Corresponds to the {@code -XX:+/-UseMultiplyToLenIntrinsic} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMultiplyToLenIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMultiplyToLenIntrinsic" : "-XX:-UseMultiplyToLenIntrinsic");
        return this;
    }

    /**
     * Enables or disables the {@code squareToLen} intrinsic. Corresponds to the {@code -XX:+/-UseSquareToLenIntrinsic} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSquareToLenIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSquareToLenIntrinsic" : "-XX:-UseSquareToLenIntrinsic");
        return this;
    }

    /**
     * Enables or disables the {@code mulAdd} intrinsic. Corresponds to the {@code -XX:+/-UseMulAddIntrinsic} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMulAddIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMulAddIntrinsic" : "-XX:-UseMulAddIntrinsic");
        return this;
    }

    /**
     * Enables or disables the Montgomery multiply intrinsic. Corresponds to the {@code -XX:+/-UseMontgomeryMultiplyIntrinsic} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMontgomeryMultiplyIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMontgomeryMultiplyIntrinsic" : "-XX:-UseMontgomeryMultiplyIntrinsic");
        return this;
    }

    /**
     * Enables or disables the Montgomery square intrinsic. Corresponds to the {@code -XX:+/-UseMontgomerySquareIntrinsic} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableMontgomerySquareIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMontgomerySquareIntrinsic" : "-XX:-UseMontgomerySquareIntrinsic");
        return this;
    }

    /**
     * Enables or disables the unconditional use of CMove instructions. Corresponds to the {@code -XX:+/-UseCMoveUnconditionally} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCMoveUnconditionally(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCMoveUnconditionally" : "-XX:-UseCMoveUnconditionally");
        return this;
    }

    /**
     * Enables or disables code cache flushing. Corresponds to the {@code -XX:+/-UseCodeCacheFlushing} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCodeCacheFlushing(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCodeCacheFlushing" : "-XX:-UseCodeCacheFlushing");
        return this;
    }

    /**
     * Enables or disables conditional card marking. Corresponds to the {@code -XX:+/-UseCondCardMark} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCondCardMark(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCondCardMark" : "-XX:-UseCondCardMark");
        return this;
    }

    /**
     * Enables or disables counted loop safepoints. Corresponds to the {@code -XX:+/-UseCountedLoopSafepoints} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableCountedLoopSafepoints(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCountedLoopSafepoints" : "-XX:-UseCountedLoopSafepoints");
        return this;
    }

    /**
     * Sets the number of iterations for loop strip mining. Corresponds to the {@code -XX:LoopStripMiningIter} flag.
     *
     * @param iterationCount The number of iterations.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder loopStripMiningIterations(int iterationCount) {
        if (iterationCount <= 0)
            throw new IllegalArgumentException("Iteration count must be positive.");

        this.arguments.add("-XX:LoopStripMiningIter=" + iterationCount);
        return this;
    }

    /**
     * Sets the number of iterations for loop strip mining in short loops. Corresponds to the {@code -XX:LoopStripMiningIterShortLoop} flag.
     *
     * @param iterationCount The number of iterations.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder loopStripMiningIterationsForShortLoops(int iterationCount) {
        if (iterationCount <= 0)
            throw new IllegalArgumentException("Iteration count must be positive.");

        this.arguments.add("-XX:LoopStripMiningIterShortLoop=" + iterationCount);
        return this;
    }

    /**
     * Enables or disables the use of Fused Multiply-Add instructions. Corresponds to the {@code -XX:+/-UseFMA} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableFMA(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseFMA" : "-XX:-UseFMA");
        return this;
    }

    /**
     * Enables or disables the use of superword optimizations. Corresponds to the {@code -XX:+/-UseSuperWord} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSuperWord(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSuperWord" : "-XX:-UseSuperWord");
        return this;
    }

    /**
     * Disables the attach mechanism. Corresponds to the {@code -XX:+DisableAttachMechanism} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableAttachMechanism() {
        this.arguments.add("-XX:+DisableAttachMechanism");
        return this;
    }

    /**
     * Enables DTrace allocation probes. Corresponds to the {@code -XX:+DTraceAllocProbes} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on macOS or Linux.
     */
    public JavaExecutableCLIBuilder enableDTraceAllocProbes() {
        if (!OperatingSystem.isLinux() && !OperatingSystem.isMac())
            throw new UnsupportedOperationException("DTrace allocation probes are only supported on macOS and Linux systems.");

        this.arguments.add("-XX:+DTraceAllocProbes");
        return this;
    }

    /**
     * Enables DTrace method probes. Corresponds to the {@code -XX:+DTraceMethodProbes} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on macOS or Linux.
     */
    public JavaExecutableCLIBuilder enableDTraceMethodProbes() {
        if (!OperatingSystem.isLinux() && !OperatingSystem.isMac())
            throw new UnsupportedOperationException("DTrace method probes are only supported on macOS and Linux systems.");

        this.arguments.add("-XX:+DTraceMethodProbes");
        return this;
    }

    /**
     * Enables DTrace monitor probes. Corresponds to the {@code -XX:+DTraceMonitorProbes} flag.
     *
     * @return This builder instance.
     * @throws UnsupportedOperationException if not on macOS or Linux.
     */
    public JavaExecutableCLIBuilder enableDTraceMonitorProbes() {
        if (!OperatingSystem.isLinux() && !OperatingSystem.isMac())
            throw new UnsupportedOperationException("DTrace monitor probes are only supported on macOS and Linux systems.");

        this.arguments.add("-XX:+DTraceMonitorProbes");
        return this;
    }

    /**
     * Enables dumping of the heap to a file on an {@link OutOfMemoryError}. Corresponds to the {@code -XX:+HeapDumpOnOutOfMemoryError} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableDumpingHeapOnOutOfMemoryError() {
        this.arguments.add("-XX:+HeapDumpOnOutOfMemoryError");
        return this;
    }

    /**
     * Sets the path for the heap dump file. Corresponds to the {@code -XX:HeapDumpPath} flag.
     *
     * @param dumpPath The path to the heap dump file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder heapDumpPath(Path dumpPath) {
        Objects.requireNonNull(dumpPath, "Heap dump path cannot be null");
        this.arguments.add("-XX:HeapDumpPath=" + dumpPath);
        return this;
    }

    /**
     * Sets the path for the log file. Corresponds to the {@code -XX:LogFile} flag.
     *
     * @param logFilePath The path to the log file.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder logFile(Path logFilePath) {
        Objects.requireNonNull(logFilePath, "Log file path cannot be null");
        this.arguments.add("-XX:LogFile=" + logFilePath);
        return this;
    }

    /**
     * Enables printing of a class instance histogram after a {@code Control+C} event. Corresponds to the {@code -XX:+PrintClassHistogram} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enablePrintingClassHistogram() {
        this.arguments.add("-XX:+PrintClassHistogram");
        return this;
    }

    /**
     * Enables printing of concurrent locks after a {@code Control+C} event. Corresponds to the {@code -XX:+PrintConcurrentLocks} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder printConcurrentLocks() {
        this.arguments.add("-XX:+PrintConcurrentLocks");
        return this;
    }

    /**
     * Prints the ranges of all VM flags. Corresponds to the {@code -XX:+PrintFlagRanges} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder printFlagRanges() {
        this.arguments.add("-XX:+PrintFlagRanges");
        return this;
    }

    /**
     * Saves performance data to a file on exit. Corresponds to the {@code -XX:+PerfDataSaveToFile} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder perfDataSaveToFile() {
        this.arguments.add("-XX:+PerfDataSaveToFile");
        return this;
    }

    /**
     * Enables or disables the use of performance data. Corresponds to the {@code -XX:+/-UsePerfData} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableUsingPerfData(boolean enable) {
        this.arguments.add(enable ? "-XX:+UsePerfData" : "-XX:-UsePerfData");
        return this;
    }

    /**
     * Enables aggressive heap management. Corresponds to the {@code -XX:+AggressiveHeap} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAggressiveHeap() {
        this.arguments.add("-XX:+AggressiveHeap");
        return this;
    }

    /**
     * Pre-touches all pages of the heap during initialization. Corresponds to the {@code -XX:+AlwaysPreTouch} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder alwaysPreTouch() {
        this.arguments.add("-XX:+AlwaysPreTouch");
        return this;
    }

    /**
     * Sets the number of threads for concurrent garbage collection. Corresponds to the {@code -XX:ConcGCThreads} flag.
     *
     * @param threadCount The number of threads.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder concurrentGCThreads(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("Thread count must be positive.");

        this.arguments.add("-XX:ConcGCThreads=" + threadCount);
        return this;
    }

    /**
     * Disables explicit garbage collection calls (e.g., from {@code System.gc()}). Corresponds to the {@code -XX:+DisableExplicitGC} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder disableExplicitGC() {
        this.arguments.add("-XX:+DisableExplicitGC");
        return this;
    }

    /**
     * Enables concurrent invocation of explicit garbage collection. Corresponds to the {@code -XX:+ExplicitGCInvokesConcurrent} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableConcurrentExplicitGCInvokes() {
        this.arguments.add("-XX:+ExplicitGCInvokesConcurrent");
        return this;
    }

    /**
     * Sets the number of initial samples for G1 adaptive IHOP. Corresponds to the {@code -XX:G1AdaptiveIHOPNumInitialSamples} flag.
     *
     * @param sampleCount The number of samples.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1AdaptiveIHOPNumInitialSamples(int sampleCount) {
        if (sampleCount < 0)
            throw new IllegalArgumentException("Sample count cannot be negative.");

        this.arguments.add("-XX:G1AdaptiveIHOPNumInitialSamples=" + sampleCount);
        return this;
    }

    /**
     * Sets the size of a G1 heap region. Corresponds to the {@code -XX:G1HeapRegionSize} flag.
     *
     * @param size The size of the region.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1HeapRegionSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap region size unit cannot be null");

        if (unit.toBytes(size) < ByteUnit.MEGABYTES.toBytes(1) || unit.toBytes(size) > ByteUnit.MEGABYTES.toBytes(32))
            throw new IllegalArgumentException("G1 heap region size must be between 1 MB and 32 MB.");

        this.arguments.add("-XX:G1HeapRegionSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the percentage of heap waste allowed for G1 GC. Corresponds to the {@code -XX:G1HeapWastePercent} flag.
     *
     * @param percent The percentage of heap waste.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1HeapWastePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Heap waste percent must be between 0 and 100.");

        this.arguments.add("-XX:G1HeapWastePercent=" + percent);
        return this;
    }

    /**
     * Sets the maximum percentage of the heap to be used for the young generation with G1 GC. Corresponds to the {@code -XX:G1MaxNewSizePercent} flag.
     *
     * @param percent The percentage of the heap.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1MaxNewSizePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max new size percent must be between 0 and 100.");

        this.arguments.add("-XX:G1MaxNewSizePercent=" + percent);
        return this;
    }

    /**
     * Sets the target number of mixed GCs after a marking cycle for G1 GC. Corresponds to the {@code -XX:G1MixedGCCountTarget} flag.
     *
     * @param targetCount The target number of mixed GCs.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1MixedGCCountTarget(int targetCount) {
        if (targetCount < 0)
            throw new IllegalArgumentException("Target count cannot be negative.");

        this.arguments.add("-XX:G1MixedGCCountTarget=" + targetCount);
        return this;
    }

    /**
     * Sets the live threshold percentage for a region to be included in a mixed GC cycle with G1 GC. Corresponds to the {@code -XX:G1MixedGCLiveThresholdPercent} flag.
     *
     * @param percent The live threshold percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1MixedGCLiveThresholdPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Live threshold percent must be between 0 and 100.");

        this.arguments.add("-XX:G1MixedGCLiveThresholdPercent=" + percent);
        return this;
    }

    /**
     * Sets the percentage of the heap to use for the young generation with G1 GC. Corresponds to the {@code -XX:G1NewSizePercent} flag.
     *
     * @param percent The percentage of the heap.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1NewSizePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("New size percent must be between 0 and 100.");

        this.arguments.add("-XX:G1NewSizePercent=" + percent);
        return this;
    }

    /**
     * Sets the threshold percentage for including old regions in a G1 collection set. Corresponds to the {@code -XX:G1OldCSetRegionThresholdPercent} flag.
     *
     * @param percent The threshold percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1OldCSetRegionThresholdPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Old CSet region threshold percent must be between 0 and 100.");

        this.arguments.add("-XX:G1OldCSetRegionThresholdPercent=" + percent);
        return this;
    }

    /**
     * Sets the percentage of the heap to reserve for G1 GC to reduce the risk of promotion failure. Corresponds to the {@code -XX:G1ReservePercent} flag.
     *
     * @param percent The percentage of the heap to reserve.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder G1ReservePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Reserve percent must be between 0 and 100.");

        this.arguments.add("-XX:G1ReservePercent=" + percent);
        return this;
    }

    /**
     * Enables G1 adaptive IHOP. Corresponds to the {@code -XX:+G1UseAdaptiveIHOP} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableG1AdaptiveIHOP() {
        this.arguments.add("-XX:+G1UseAdaptiveIHOP");
        return this;
    }

    /**
     * Sets the initial heap size. Corresponds to the {@code -XX:InitialHeapSize} flag.
     *
     * @param size The initial heap size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder initialHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Initial heap size cannot be negative.");

        if (unit.toBytes(size) < ByteUnit.MEGABYTES.toBytes(1) || (unit.toBytes(size) % 1024) != 0)
            throw new IllegalArgumentException("Initial heap size must be at least 1 MB and a multiple of 1024 bytes.");

        this.arguments.add("-XX:InitialHeapSize" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the initial survivor space ratio. Corresponds to the {@code -XX:InitialSurvivorRatio} flag.
     *
     * @param ratio The initial survivor space ratio.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder initialSurvivorRatio(int ratio) {
        if (ratio <= 0)
            throw new IllegalArgumentException("Initial survivor ratio must be positive.");

        this.arguments.add("-XX:InitialSurvivorRatio=" + ratio);
        return this;
    }

    /**
     * Sets the heap occupancy percentage at which to start a concurrent GC cycle. Corresponds to the {@code -XX:InitiatingHeapOccupancyPercent} flag.
     *
     * @param percent The heap occupancy percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder initiatingHeapOccupancyPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Initiating heap occupancy percent must be between 0 and 100.");

        this.arguments.add("-XX:InitiatingHeapOccupancyPercent=" + percent);
        return this;
    }

    /**
     * Sets the maximum GC pause time goal. Corresponds to the {@code -XX:MaxGCPauseMillis} flag.
     *
     * @param time The maximum pause time.
     * @param unit The unit of time.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxGCPause(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Max GC pause time cannot be negative.");

        this.arguments.add("-XX:MaxGCPauseMillis=" + unit.toMillis(time));
        return this;
    }

    /**
     * Sets the maximum heap size. Corresponds to the {@code -XX:MaxHeapSize} flag.
     *
     * @param size The maximum heap size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max heap size cannot be negative.");

        if (unit.toBytes(size) < ByteUnit.MEGABYTES.toBytes(2) || (unit.toBytes(size) % 1024) != 0)
            throw new IllegalArgumentException("Max heap size must be at least 2 MB and a multiple of 1024 bytes.");

        this.arguments.add("-XX:MaxHeapSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum percentage of free heap space after a GC to avoid shrinking. Corresponds to the {@code -XX:MaxHeapFreeRatio} flag.
     *
     * @param percent The maximum free heap ratio percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxHeapFreeRatioPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max heap free ratio percent must be between 0 and 100.");

        this.arguments.add("-XX:MaxHeapFreeRatio=" + percent);
        return this;
    }

    /**
     * Sets the maximum metaspace size. Corresponds to the {@code -XX:MaxMetaspaceSize} flag.
     *
     * @param size The maximum metaspace size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxMetaspaceSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Metaspace size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max metaspace size cannot be negative.");

        this.arguments.add("-XX:MaxMetaspaceSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum size of the young generation. Corresponds to the {@code -XX:MaxNewSize} flag.
     *
     * @param size The maximum size of the young generation.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxNewSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "New size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max new size cannot be negative.");

        this.arguments.add("-XX:MaxNewSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum RAM to be used by the JVM. Corresponds to the {@code -XX:MaxRAM} flag.
     *
     * @param size The maximum RAM size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxRAM(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "RAM size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max RAM size cannot be negative.");

        if (unit.toBytes(size) > ByteUnit.GIGABYTES.toBytes(128))
            throw new IllegalArgumentException("Max RAM size cannot exceed 128 GB.");

        this.arguments.add("-XX:MaxRAM=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the maximum percentage of RAM to be used by the JVM. Corresponds to the {@code -XX:MaxRAMPercentage} flag.
     *
     * @param percent The maximum RAM percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxRAMPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max RAM percent must be between 0 and 100.");

        this.arguments.add("-XX:MaxRAMPercent=" + percent);
        return this;
    }

    /**
     * Sets the maximum tenuring threshold for promoting objects to the old generation. Corresponds to the {@code -XX:MaxTenuringThreshold} flag.
     *
     * @param threshold The maximum tenuring threshold.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxTenuringThreshold(int threshold) {
        if (threshold < 0)
            throw new IllegalArgumentException("Max tenuring threshold cannot be negative.");

        if (threshold > 15)
            throw new IllegalArgumentException("Max tenuring threshold cannot exceed 15.");

        this.arguments.add("-XX:MaxTenuringThreshold=" + threshold);
        return this;
    }

    /**
     * Sets the initial metaspace size. Corresponds to the {@code -XX:MetaspaceSize} flag.
     *
     * @param size The initial metaspace size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder metaspaceSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Metaspace size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Metaspace size cannot be negative.");

        this.arguments.add("-XX:MetaspaceSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the minimum percentage of free heap space after a GC to avoid expansion. Corresponds to the {@code -XX:MinHeapFreeRatio} flag.
     *
     * @param percent The minimum free heap ratio percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder minHeapFreeRatioPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Min heap free ratio percent must be between 0 and 100.");

        this.arguments.add("-XX:MinHeapFreeRatio=" + percent);
        return this;
    }

    /**
     * Sets the ratio between the young and old generation sizes. Corresponds to the {@code -XX:NewRatio} flag.
     *
     * @param ratio The ratio of the young to the old generation.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder newRatio(int ratio) {
        if (ratio <= 0)
            throw new IllegalArgumentException("New ratio must be positive.");

        this.arguments.add("-XX:NewRatio=" + ratio);
        return this;
    }

    /**
     * Sets the initial size of the young generation. Corresponds to the {@code -XX:NewSize} flag.
     *
     * @param size The initial size of the young generation.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder newSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "New size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("New size cannot be negative.");

        this.arguments.add("-XX:NewSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Sets the number of threads for parallel garbage collection. Corresponds to the {@code -XX:ParallelGCThreads} flag.
     *
     * @param threadCount The number of threads.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder parallelGCThreads(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("Thread count must be positive.");

        this.arguments.add("-XX:ParallelGCThreads=" + threadCount);
        return this;
    }

    /**
     * Enables or disables parallel reference processing. Corresponds to the {@code -XX:+/-ParallelRefProcEnabled} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableParallelRefProc(boolean enable) {
        this.arguments.add(enable ? "-XX:+ParallelRefProcEnabled" : "-XX:-ParallelRefProcEnabled");
        return this;
    }

    /**
     * Enables printing of adaptive generation sizing information. Corresponds to the {@code -XX:+PrintAdaptiveSizePolicy} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enablePrintingAdaptiveSizePolicy() {
        this.arguments.add("-XX:+PrintAdaptiveSizePolicy");
        return this;
    }

    /**
     * Sets the amount of time a softly reachable object will be kept alive for each megabyte of free heap space. Corresponds to the {@code -XX:SoftRefLRUPolicyMSPerMB} flag.
     *
     * @param time The time to keep softly reachable objects alive.
     * @param unit The unit of time.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder softRefLRUPolicyMSPerMB(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Time cannot be negative.");

        this.arguments.add("-XX:SoftRefLRUPolicyMSPerMB=" + unit.toMillis(time));
        return this;
    }

    /**
     * Incrementally reduces the Java heap size. Corresponds to the {@code -XX:-ShrinkHeapInSteps} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder incrementallyReduceJavaHeapSize() {
        this.arguments.add("-XX:-ShrinkHeapInSteps");
        return this;
    }

    /**
     * Sets the age threshold for string deduplication. Corresponds to the {@code -XX:StringDeduplicationAgeThreshold} flag.
     *
     * @param threshold The age threshold.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder stringDeduplicationAgeThreshold(int threshold) {
        if (threshold < 0)
            throw new IllegalArgumentException("String deduplication age threshold cannot be negative.");

        this.arguments.add("-XX:StringDeduplicationAgeThreshold=" + threshold);
        return this;
    }

    /**
     * Sets the ratio of survivor space to eden space. Corresponds to the {@code -XX:SurvivorRatio} flag.
     *
     * @param ratio The survivor space ratio.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder survivorRatio(int ratio) {
        if (ratio <= 0)
            throw new IllegalArgumentException("Survivor ratio must be positive.");

        this.arguments.add("-XX:SurvivorRatio=" + ratio);
        return this;
    }

    /**
     * Sets the target survivor space occupancy percentage after a young GC. Corresponds to the {@code -XX:TargetSurvivorRatio} flag.
     *
     * @param percent The target survivor ratio percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder targetSurvivorRatioPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Target survivor ratio percent must be between 0 and 100.");

        this.arguments.add("-XX:TargetSurvivorRatio=" + percent);
        return this;
    }

    /**
     * Sets the size of a thread-local allocation buffer (TLAB). Corresponds to the {@code -XX:TLABSize} flag.
     *
     * @param size The TLAB size.
     * @param unit The unit of size.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder TLABSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "TLAB size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("TLAB size cannot be negative.");

        this.arguments.add("-XX:TLABSize=" + size + unit.getUnit());
        return this;
    }

    /**
     * Enables or disables adaptive generation sizing. Corresponds to the {@code -XX:+/-UseAdaptiveSizePolicy} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableAdaptiveSizePolicy(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAdaptiveSizePolicy" : "-XX:-UseAdaptiveSizePolicy");
        return this;
    }

    /**
     * Enables the G1 garbage collector. Corresponds to the {@code -XX:+UseG1GC} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableG1GC() {
        this.arguments.add("-XX:+UseG1GC");
        return this;
    }

    /**
     * Enables the GC overhead limit. Corresponds to the {@code -XX:+UseGCOverheadLimit} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableGCOverheadLimit() {
        this.arguments.add("-XX:+UseGCOverheadLimit");
        return this;
    }

    /**
     * Enables NUMA-aware memory allocation. Corresponds to the {@code -XX:+UseNUMA} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableNUMA() {
        this.arguments.add("-XX:+UseNUMA");
        return this;
    }

    /**
     * Enables the parallel garbage collector. Corresponds to the {@code -XX:+UseParallelGC} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableParallelGC() {
        this.arguments.add("-XX:+UseParallelGC");
        return this;
    }

    /**
     * Enables the serial garbage collector. Corresponds to the {@code -XX:+UseSerialGC} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableSerialGC() {
        this.arguments.add("-XX:+UseSerialGC");
        return this;
    }

    /**
     * Enables or disables string deduplication. Corresponds to the {@code -XX:+/-UseStringDeduplication} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableStringDeduplication(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseStringDeduplication" : "-XX:-UseStringDeduplication");
        return this;
    }

    /**
     * Enables or disables thread-local allocation buffers (TLABs). Corresponds to the {@code -XX:+/-UseTLAB} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableTLAB(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseTLAB" : "-XX:-UseTLAB");
        return this;
    }

    /**
     * Enables the Z garbage collector (ZGC). Corresponds to the {@code -XX:+UseZGC} flag.
     *
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableZGC() {
        this.arguments.add("-XX:+UseZGC");
        return this;
    }

    /**
     * Sets the allocation spike tolerance for ZGC. Corresponds to the {@code -XX:ZAllocationSpikeTolerance} flag.
     *
     * @param tolerance The allocation spike tolerance.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder allocationSpikeToleranceForZGC(float tolerance) {
        this.arguments.add("-XX:ZAllocationSpikeTolerance=" + tolerance);
        return this;
    }

    /**
     * Sets the maximum collection interval for ZGC. Corresponds to the {@code -XX:ZCollectionInterval} flag.
     *
     * @param time The maximum collection interval.
     * @param unit The unit of time.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxCollectionIntervalForZGC(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Max collection interval cannot be negative.");

        this.arguments.add("-XX:ZCollectionInterval=" + unit.toSeconds(time));
        return this;
    }

    /**
     * Sets the maximum fragmentation limit percentage for ZGC. Corresponds to the {@code -XX:ZFragmentationLimit} flag.
     *
     * @param percent The maximum fragmentation limit percentage.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder maxFragmentationLimitPercentForZGC(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max fragmentation limit percent must be between 0 and 100.");

        this.arguments.add("-XX:ZFragmentationLimit=" + percent);
        return this;
    }

    /**
     * Enables or disables proactive GC cycles for ZGC. Corresponds to the {@code -XX:+/-ZProactiveGCCycles} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableProactiveGCCyclesForZGC(boolean enable) {
        this.arguments.add(enable ? "-XX:+ZProactiveGCCycles" : "-XX:-ZProactiveGCCycles");
        return this;
    }

    /**
     * Enables or disables uncommitting unused memory for ZGC. Corresponds to the {@code -XX:+/-ZUncommit} flag.
     *
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder enableUncommitForZGC(boolean enable) {
        this.arguments.add(enable ? "-XX:+ZUncommit" : "-XX:-ZUncommit");
        return this;
    }

    /**
     * Sets the uncommit delay for ZGC. Corresponds to the {@code -XX:ZUncommitDelay} flag.
     *
     * @param time The uncommit delay.
     * @param unit The unit of time.
     * @return This builder instance.
     */
    public JavaExecutableCLIBuilder uncommitDelayForZGC(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Uncommit delay cannot be negative.");

        this.arguments.add("-XX:ZUncommitDelay=" + unit.toSeconds(time));
        return this;
    }

    @Override
    public Process run() {
        List<String> command = new ArrayList<>();
        command.add(jdk.executablePath(resolveExecutableName(enableConsole)).toString());
        String preArgument = launchType.getPreArgument();
        if (!preArgument.isEmpty()) {
            command.add(preArgument);
        }
        command.add(primaryArgument);
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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, resolveExecutableName(enableConsole));
            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start Java process", exception);
        }
    }

    /**
     * Resolves the name of the Java executable based on the operating system and console enablement.
     *
     * @param enableConsole {@code true} if a console is enabled (Windows only), {@code false} otherwise.
     * @return The name of the executable (e.g., "java.exe", "javaw.exe", "java").
     */
    private static String resolveExecutableName(boolean enableConsole) {
        if (OperatingSystem.isWindows() && !enableConsole)
            return "javaw.exe";

        return JDKManager.JAVA_EXECUTABLE_NAME;
    }

    /**
     * Represents the different ways a Java application can be launched.
     */
    @Getter
    public enum LaunchType {
        CLASS_FILE,
        JAR_FILE("-jar"),
        MODULE("-m"),
        SOURCE_FILE;

        private final String preArgument;

        LaunchType(String preArgument) {
            this.preArgument = preArgument;
        }

        LaunchType() {
            this.preArgument = "";
        }
    }

    /**
     * Represents different access modes for certain VM options.
     */
    @Getter
    public enum AccessMode {
        ALLOW("allow"),
        WARN("warn"),
        DENY("deny"),
        DEBUG("debug");

        private final String mode;

        AccessMode(String mode) {
            this.mode = mode;
        }
    }

    /**
     * Represents an enabled or disabled state.
     */
    @Getter
    public enum EnabledDisabled {
        ENABLED,
        DISABLED;

        private final String state;

        EnabledDisabled() {
            this.state = name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Represents predefined sets of root modules.
     */
    @Getter
    public enum RootModule {
        ALL_DEFAULT,
        ALL_SYSTEM,
        ALL_MODULE_PATH;

        private final String module;

        RootModule() {
            this.module = name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * Represents components for which verbose output can be enabled.
     */
    @Getter
    public enum VerboseComponent {
        CLASS,
        GC,
        JNI,
        MODULE;

        private final String component;

        VerboseComponent() {
            this.component = name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A builder for configuring logging options for the JVM.
     */
    public static final class LoggingConfiguration {
        private final List<LogSelection> selections = new ArrayList<>();
        private LogOutput output;
        private final EnumSet<LogDecorator> decorators = EnumSet.noneOf(LogDecorator.class);

        public LoggingConfiguration select(LogSelection selection) {
            Objects.requireNonNull(selection, "Log selection cannot be null");
            this.selections.add(selection);
            return this;
        }

        public LoggingConfiguration select(LogSelection... selectionArray) {
            Objects.requireNonNull(selectionArray, "Log selections cannot be null");
            for (LogSelection selection : selectionArray) {
                select(selection);
            }

            return this;
        }

        public LoggingConfiguration output(LogOutput output) {
            this.output = Objects.requireNonNull(output, "Log output cannot be null");
            return this;
        }

        public LoggingConfiguration decorators(LogDecorator... decoratorArray) {
            Objects.requireNonNull(decoratorArray, "Log decorators cannot be null");
            for (LogDecorator decorator : decoratorArray) {
                Objects.requireNonNull(decorator, "Decorator cannot be null");
                this.decorators.add(decorator);
            }

            return this;
        }

        private String asArgument() {
            StringBuilder builder = new StringBuilder();
            if (!selections.isEmpty()) {
                builder.append(selections.stream().map(LogSelection::asToken).collect(Collectors.joining(",")));
            }

            boolean hasOutput = output != null;
            boolean hasDecorators = !decorators.isEmpty();
            boolean hasOutputOptions = hasOutput && output.hasOptions();

            if (hasOutput || hasDecorators) {
                if (!builder.isEmpty())
                    builder.append(":");

                if (hasOutput) {
                    builder.append(output.destination());
                }
            }

            if (hasDecorators || hasOutputOptions) {
                builder.append(":");
                if (hasDecorators) {
                    builder.append(decorators.stream().map(LogDecorator::token).collect(Collectors.joining(",")));
                }
            }

            if (hasOutputOptions) {
                builder.append(":").append(output.optionsToken());
            }

            return builder.toString();
        }
    }

    /**
     * Represents a selection of log tags and a log level for logging configuration.
     */
    public static final class LogSelection {
        private final List<String> tags = new ArrayList<>();
        private LogLevel level = LogLevel.INFO;
        private String literalToken;

        public static LogSelection create() {
            return new LogSelection();
        }

        public static LogSelection literal(String token) {
            LogSelection selection = new LogSelection();
            selection.literalToken = Objects.requireNonNull(token, "Log selector literal cannot be null");
            return selection;
        }

        public static LogSelection all(LogLevel level) {
            return create().level(level);
        }

        public LogSelection tags(String... tags) {
            Objects.requireNonNull(tags, "Log tags cannot be null");
            for (String tag : tags) {
                if (tag == null || tag.isBlank())
                    throw new IllegalArgumentException("Log tag cannot be null or blank");
                this.tags.add(tag);
            }

            return this;
        }

        public LogSelection level(LogLevel level) {
            this.level = Objects.requireNonNull(level, "Log level cannot be null");
            return this;
        }

        private String asToken() {
            if (literalToken != null) {
                return literalToken;
            }

            String selector = tags.isEmpty() ? "*" : String.join("+", tags);
            return selector + "=" + level.token();
        }
    }

    /**
     * Represents different logging levels.
     */
    public enum LogLevel {
        TRACE("trace"),
        DEBUG("debug"),
        INFO("info"),
        WARNING("warning"),
        ERROR("error"),
        OFF("off");

        private final String token;

        LogLevel(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /**
     * Represents different decorators for log output.
     */
    public enum LogDecorator {
        TIME("time"),
        UPTIME("uptime"),
        TIMEMILLIS("timemillis"),
        UPTIMEMILLIS("uptimemillis"),
        TIMEDELTA("timedelta"),
        USTAMP("ustamp"),
        PID("pid"),
        TID("tid"),
        LEVEL("level"),
        TAGS("tags"),
        HOSTNAME("hostname");

        private final String token;

        LogDecorator(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    /**
     * Represents the output destination for logging.
     */
    public static final class LogOutput {
        private final String destination;
        private final List<String> options = new ArrayList<>();

        private LogOutput(String destination) {
            this.destination = Objects.requireNonNull(destination, "Log output destination cannot be null");
        }

        public static LogOutput stdout() {
            return new LogOutput("stdout");
        }

        public static LogOutput stderr() {
            return new LogOutput("stderr");
        }

        public static LogOutput file(Path path) {
            Objects.requireNonNull(path, "Log file path cannot be null");
            return new LogOutput("file=" + path);
        }

        public static LogOutput custom(String destination) {
            return new LogOutput(destination);
        }

        public LogOutput option(String key, String value) {
            Objects.requireNonNull(key, "Output option key cannot be null");
            Objects.requireNonNull(value, "Output option value cannot be null");
            this.options.add(key + "=" + value);
            return this;
        }

        public LogOutput rotateFiles(int fileCount, long fileSize, ByteUnit unit) {
            if (fileCount <= 0)
                throw new IllegalArgumentException("File count must be positive");
            if (fileSize <= 0)
                throw new IllegalArgumentException("File size must be positive");
            Objects.requireNonNull(unit, "Byte unit cannot be null");
            this.options.add("filecount=" + fileCount);
            this.options.add("filesize=" + fileSize + unit.getUnit());
            return this;
        }

        private boolean hasOptions() {
            return !options.isEmpty();
        }

        private String optionsToken() {
            return String.join(",", options);
        }

        private String destination() {
            return destination;
        }
    }

    /**
     * Represents different byte units for specifying sizes.
     */
    @Getter
    public enum ByteUnit {
        BYTES(""),
        KILOBYTES("K"),
        MEGABYTES("M"),
        GIGABYTES("G");

        private static final long MULTIPLIER = 1024L;

        private final String unit;

        ByteUnit(String unit) {
            this.unit = unit;
        }

        public long toBytes(long size) {
            return switch (this) {
                case BYTES -> size;
                case KILOBYTES -> BYTES.toBytes(size) * MULTIPLIER;
                case MEGABYTES -> KILOBYTES.toBytes(size) * MULTIPLIER;
                case GIGABYTES -> MEGABYTES.toBytes(size) * MULTIPLIER;
            };
        }
    }

    /**
     * Represents an auto, on, or off state.
     */
    @Getter
    public enum AutoOnOff {
        AUTO,
        ON,
        OFF;

        private final String mode;

        AutoOnOff() {
            this.mode = name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Represents different categories of JVM settings.
     */
    @Getter
    public enum SettingCategory {
        ALL,
        LOCALE,
        PROPERTIES,
        SECURITY_ALL,
        SECURITY_PROPERTIES,
        SECURITY_PROVIDERS,
        SECURITY_TLS,
        VM,
        SYSTEM;

        private final String categoryName;

        SettingCategory() {
            this.categoryName = name().toLowerCase(Locale.ROOT).replace('_', ':');
        }
    }

    /**
     * Represents an option for Java Flight Recorder (JFR).
     */
    public record FlightRecorderOption(String name, String value) {
        public static FlightRecorderOption globalBufferSize(long size, ByteUnit unit) {
            Objects.requireNonNull(unit, "Byte unit cannot be null");

            if (size < 0)
                throw new IllegalArgumentException("Global buffer size cannot be negative.");

            return new FlightRecorderOption("globalbuffersize", size + unit.getUnit());
        }

        public static FlightRecorderOption maxChunkSize(long size, ByteUnit unit) {
            Objects.requireNonNull(unit, "Byte unit cannot be null");

            if (size < 0)
                throw new IllegalArgumentException("Max chunk size cannot be negative.");

            return new FlightRecorderOption("maxchunksize", size + unit.getUnit());
        }

        public static FlightRecorderOption memorySize(long size, ByteUnit unit) {
            Objects.requireNonNull(unit, "Byte unit cannot be null");

            if (size < 0)
                throw new IllegalArgumentException("Memory size cannot be negative.");

            return new FlightRecorderOption("memorysize", size + unit.getUnit());
        }

        public static FlightRecorderOption numGlobalBuffers(int count) {
            if (count < 0)
                throw new IllegalArgumentException("Number of global buffers cannot be negative.");

            return new FlightRecorderOption("numglobalbuffers", Integer.toString(count));
        }

        public static FlightRecorderOption oldObjectQueueSize(int size) {
            if (size < 0)
                throw new IllegalArgumentException("Old object queue size cannot be negative.");

            return new FlightRecorderOption("oldobjectqueuesize", Integer.toString(size));
        }

        public static FlightRecorderOption preserveRepository(boolean preserve) {
            return new FlightRecorderOption("preserverecording", Boolean.toString(preserve));
        }

        public static FlightRecorderOption repositoryPath(Path path) {
            Objects.requireNonNull(path, "Path cannot be null");
            return new FlightRecorderOption("repositorypath", path.toString());
        }

        public static FlightRecorderOption retransformEventClasses(boolean retransform) {
            return new FlightRecorderOption("retransform", Boolean.toString(retransform));
        }

        public static FlightRecorderOption stackDepth(int depth) {
            if (depth < 0)
                throw new IllegalArgumentException("Stack depth cannot be negative.");

            return new FlightRecorderOption("stackdepth", Integer.toString(depth));
        }

        public static FlightRecorderOption threadBufferSize(long size, ByteUnit unit) {
            Objects.requireNonNull(unit, "Byte unit cannot be null");
            return new FlightRecorderOption("threadbuffersize", size + unit.getUnit());
        }
    }

    /**
     * Represents parameters for starting a Java Flight Recorder (JFR) recording.
     */
    public record FlightRecorderParameters(String name, String value) {
        public static FlightRecorderParameters delay(long duration, TimeUnit unit) {
            Objects.requireNonNull(unit, "TimeUnit cannot be null");

            if (duration < 0)
                throw new IllegalArgumentException("Delay duration cannot be negative.");

            return new FlightRecorderParameters("delay", Long.toString(unit.toMillis(duration)));
        }

        public static FlightRecorderParameters writeToDisk(boolean toDisk) {
            return new FlightRecorderParameters("disk", Boolean.toString(toDisk));
        }

        public static FlightRecorderParameters dumpOnExit(boolean dump) {
            return new FlightRecorderParameters("dumponexit", Boolean.toString(dump));
        }

        public static FlightRecorderParameters duration(long duration, TimeUnit unit) {
            Objects.requireNonNull(unit, "TimeUnit cannot be null");

            if (duration < 0)
                throw new IllegalArgumentException("Duration cannot be negative.");

            return new FlightRecorderParameters("duration", Long.toString(unit.toMillis(duration)));
        }

        public static FlightRecorderParameters filename(Path path) {
            Objects.requireNonNull(path, "Path cannot be null");
            return new FlightRecorderParameters("filename", path.toString());
        }

        public static FlightRecorderParameters name(String identifier) {
            Objects.requireNonNull(identifier, "Identifier cannot be null");
            return new FlightRecorderParameters("name", identifier);
        }

        public static FlightRecorderParameters maxAge(long age, TimeUnit unit) {
            Objects.requireNonNull(unit, "TimeUnit cannot be null");

            if (age < 0)
                throw new IllegalArgumentException("Max age cannot be negative.");

            if (unit != TimeUnit.SECONDS && unit != TimeUnit.MINUTES && unit != TimeUnit.HOURS && unit != TimeUnit.DAYS)
                throw new IllegalArgumentException("Max age must be specified in seconds, minutes, hours, or days.");

            return new FlightRecorderParameters("maxage", age + unit.toString().substring(0, 1).toLowerCase(Locale.ROOT));
        }

        public static FlightRecorderParameters maxSize(long size, ByteUnit unit) {
            Objects.requireNonNull(unit, "Byte unit cannot be null.");

            if (size < 0)
                throw new IllegalArgumentException("Max size cannot be negative.");

            if (unit == ByteUnit.BYTES || unit == ByteUnit.KILOBYTES)
                throw new IllegalArgumentException("Max size must be specified in MB or GB.");

            return new FlightRecorderParameters("maxsize", size + unit.getUnit());
        }

        public static FlightRecorderParameters collectPathToGCRoots(boolean collect) {
            return new FlightRecorderParameters("path-to-gc-roots", Boolean.toString(collect));
        }

        public static FlightRecorderParameters nameToReportOnExit(String name) {
            Objects.requireNonNull(name, "Name cannot be null");
            return new FlightRecorderParameters("report-on-exit", name);
        }

        public static FlightRecorderParameters settingsFile(Path path) {
            Objects.requireNonNull(path, "Path cannot be null");
            return new FlightRecorderParameters("settings", path.toString());
        }

        public static FlightRecorderParameters option(String value) {
            Objects.requireNonNull(value, "Option value cannot be null");
            return new FlightRecorderParameters("option", value);
        }

        public static FlightRecorderParameters eventSetting(String value) {
            Objects.requireNonNull(value, "Event setting value cannot be null");
            return new FlightRecorderParameters("event-setting", value);
        }

        @Override
        public @NotNull String toString() {
            return name + "=" + value;
        }
    }

    /**
     * Represents different modes for native memory tracking.
     */
    @Getter
    public enum NativeMemoryTracking {
        OFF,
        SUMMARY,
        DETAIL;

        private final String state;

        NativeMemoryTracking() {
            this.state = name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Represents different branch protection modes for AArch64 Linux.
     */
    @Getter
    public enum BranchProtectionMode {
        NONE("none"),
        STANDARD("standard"),
        PAC_RET("pac-ret");

        private final String mode;

        BranchProtectionMode(String mode) {
            this.mode = mode;
        }
    }

    /**
     * Represents different prefetch styles for allocation.
     */
    public enum PrefetchStyle {
        DO_NOT(0),
        AFTER_ALLOCATE(1),
        TLAB_WATERMARK_POINTER(2),
        PER_CACHE_LINE(3);

        private final int styleInt;

        PrefetchStyle(int styleInt) {
            this.styleInt = styleInt;
        }

        public int asInt() {
            return styleInt;
        }
    }

    /**
     * Represents different commands for the JIT compiler.
     */
    @Getter
    public enum CompileCommand {
        BREAK("break"),
        COMPILE_ONLY("compileonly"),
        DO_NOT_INLINE("dontinline"),
        EXCLUDE("exclude"),
        HELP("help"),
        INLINE("inline"),
        LOG("log"),
        OPTION("option"),
        PRINT("print"),
        QUIET("quiet");

        private final String command;

        CompileCommand(String command) {
            this.command = command;
        }
    }

    /**
     * Represents different data models (32-bit or 64-bit).
     */
    @Getter
    public enum DataModel {
        DATA_32("d32"),
        DATA_64("d64");

        private final String model;

        DataModel(String model) {
            this.model = model;
        }
    }
}
