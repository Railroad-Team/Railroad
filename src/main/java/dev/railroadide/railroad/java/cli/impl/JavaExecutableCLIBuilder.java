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

    public static JavaExecutableCLIBuilder classFile(JDK jdk, Path classFilePath) {
        Objects.requireNonNull(classFilePath, "Class file path cannot be null");
        if (!classFilePath.toString().endsWith(".class"))
            throw new IllegalArgumentException("Provided path is not a .class file: " + classFilePath);
        return new JavaExecutableCLIBuilder(LaunchType.CLASS_FILE, jdk, classFilePath.toString());
    }

    public static JavaExecutableCLIBuilder jarFile(JDK jdk, Path jarFilePath) {
        Objects.requireNonNull(jarFilePath, "JAR file path cannot be null");
        if (!jarFilePath.toString().endsWith(".jar"))
            throw new IllegalArgumentException("Provided path is not a .jar file: " + jarFilePath);

        return new JavaExecutableCLIBuilder(LaunchType.JAR_FILE, jdk, jarFilePath.toString());
    }

    public static JavaExecutableCLIBuilder module(JDK jdk, String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        return new JavaExecutableCLIBuilder(LaunchType.MODULE, jdk, moduleName);
    }

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

    public JavaExecutableCLIBuilder enableConsole(boolean enableConsole) {
        this.enableConsole = enableConsole;
        return this;
    }

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

    public JavaExecutableCLIBuilder classpath(String... classpathEntries) {
        Objects.requireNonNull(classpathEntries, "Classpath entries cannot be null");
        this.arguments.add("-cp " + String.join(File.pathSeparator, classpathEntries));
        return this;
    }

    public JavaExecutableCLIBuilder disableAtFiles() {
        this.arguments.add("--disable-@files");
        return this;
    }

    public JavaExecutableCLIBuilder enablePreviewFeatures() {
        if (jdk.version().major() < 12)
            throw new UnsupportedOperationException("Preview features are only supported in JDK 12 and above.");

        this.arguments.add("--enable-preview");
        return this;
    }

    public JavaExecutableCLIBuilder enableNativeAccess(String moduleName) {
        if (jdk.version().major() < 16)
            throw new UnsupportedOperationException("Enabling native access is only supported in JDK 16 and above.");

        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--enable-native-access=" + moduleName);
        return this;
    }

    public JavaExecutableCLIBuilder enableNativeAccess() {
        return enableNativeAccess("ALL-UNNAMED");
    }

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

    public JavaExecutableCLIBuilder finalization(EnabledDisabled state) {
        if (jdk.version().major() < 18)
            throw new UnsupportedOperationException("Controlling finalization is only supported in JDK 18 and above.");

        Objects.requireNonNull(state, "Finalization state cannot be null");
        this.arguments.add("--finalization=" + state.getState());
        return this;
    }

    public JavaExecutableCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JavaExecutableCLIBuilder upgradeModulePath(String... upgradeModulePaths) {
        Objects.requireNonNull(upgradeModulePaths, "Upgrade module path entries cannot be null");
        this.arguments.add("--upgrade-module-path " + String.join(File.pathSeparator, upgradeModulePaths));
        return this;
    }

    public JavaExecutableCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    public JavaExecutableCLIBuilder addModules(RootModule rootModule) {
        Objects.requireNonNull(rootModule, "Root module cannot be null");
        this.arguments.add("--add-modules " + rootModule.getModule());
        return this;
    }

    public JavaExecutableCLIBuilder listModules() {
        this.arguments.add("--list-modules");
        return this;
    }

    public JavaExecutableCLIBuilder describeModule(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        this.arguments.add("--describe-module " + moduleName);
        return this;
    }

    public JavaExecutableCLIBuilder dryRun() {
        this.arguments.add("--dry-run");
        return this;
    }

    public JavaExecutableCLIBuilder validateModules() {
        this.arguments.add("--validate-modules");
        return this;
    }

    public JavaExecutableCLIBuilder systemProperty(String key, String value) {
        Objects.requireNonNull(key, "System property key cannot be null");
        Objects.requireNonNull(value, "System property value cannot be null");
        String normalizedValue = value.startsWith("\"") && value.endsWith("\"") ? value : "\"" + value + "\"";
        this.arguments.add("-D" + key + "=" + normalizedValue);
        return this;
    }

    public JavaExecutableCLIBuilder disableAssertions() {
        this.arguments.add("-da");
        return this;
    }

    public JavaExecutableCLIBuilder disableAssertions(String packageOrClassName, boolean subpackages) {
        Objects.requireNonNull(packageOrClassName, "Package or class name cannot be null");
        this.arguments.add("-da:" + packageOrClassName + (subpackages ? "..." : ""));
        return this;
    }

    public JavaExecutableCLIBuilder disableAssertions(String packageOrClassName) {
        return disableAssertions(packageOrClassName, false);
    }

    public JavaExecutableCLIBuilder disableSystemAssertions() {
        this.arguments.add("-dsa");
        return this;
    }

    public JavaExecutableCLIBuilder enableAssertions() {
        this.arguments.add("-ea");
        return this;
    }

    public JavaExecutableCLIBuilder enableAssertions(String packageOrClassName, boolean subpackages) {
        Objects.requireNonNull(packageOrClassName, "Package or class name cannot be null");
        this.arguments.add("-ea:" + packageOrClassName + (subpackages ? "..." : ""));
        return this;
    }

    public JavaExecutableCLIBuilder enableAssertions(String packageOrClassName) {
        return enableAssertions(packageOrClassName, false);
    }

    public JavaExecutableCLIBuilder enableSystemAssertions() {
        this.arguments.add("-esa");
        return this;
    }

    public JavaExecutableCLIBuilder help(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-help" : "--help");
        return this;
    }

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

    public JavaExecutableCLIBuilder javaagent(Path javaAgentPath, String... options) {
        Objects.requireNonNull(javaAgentPath, "Java agent path cannot be null");
        return javaagent(javaAgentPath.toString(), options);
    }

    public JavaExecutableCLIBuilder showVersion(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-showversion" : "--showversion");
        return this;
    }

    public JavaExecutableCLIBuilder showModuleResolution() {
        this.arguments.add("--show-module-resolution");
        return this;
    }

    public JavaExecutableCLIBuilder splashScreen(String splashImagePath) {
        Objects.requireNonNull(splashImagePath, "Splash image path cannot be null");
        this.arguments.add("--splash:" + splashImagePath);
        return this;
    }

    public JavaExecutableCLIBuilder splashScreen(Path splashImagePath) {
        Objects.requireNonNull(splashImagePath, "Splash image path cannot be null");
        return splashScreen(splashImagePath.toString());
    }

    public JavaExecutableCLIBuilder verbose(VerboseComponent component) {
        Objects.requireNonNull(component, "Verbose component cannot be null");
        this.arguments.add("-verbose:" + component.getComponent());
        return this;
    }

    public JavaExecutableCLIBuilder version(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-version" : "--version");
        return this;
    }

    public JavaExecutableCLIBuilder extraOptionsHelp(boolean errorOutput) {
        this.arguments.add(errorOutput ? "-X" : "--help-extra");
        return this;
    }

    public JavaExecutableCLIBuilder addArgFile(Path argFilePath) {
        Objects.requireNonNull(argFilePath, "Argument file path cannot be null");
        this.arguments.add("@" + argFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder disableBackgroundCompilation() {
        this.arguments.add("-Xbatch");
        return this;
    }

    public JavaExecutableCLIBuilder appendBootClassPath(String... bootClassPathEntries) {
        Objects.requireNonNull(bootClassPathEntries, "Boot class path entries cannot be null");
        this.arguments.add("-Xbootclasspath/a:" + String.join(File.pathSeparator, bootClassPathEntries));
        return this;
    }

    public JavaExecutableCLIBuilder performAdditionalJNIChecks() {
        this.arguments.add("-Xcheck:jni");
        return this;
    }

    public JavaExecutableCLIBuilder exerciseJITCompiler() {
        this.arguments.add("-Xcomp");
        return this;
    }

    @Deprecated(forRemoval = true, since = "Java 5")
    public JavaExecutableCLIBuilder enableDebuggingSupport() {
        this.arguments.add("-Xdebug");
        return this;
    }

    public JavaExecutableCLIBuilder additionalDiagnosticMessages() {
        this.arguments.add("-Xdiag");
        return this;
    }

    public JavaExecutableCLIBuilder interpretOnlyMode() {
        this.arguments.add("-Xint");
        return this;
    }

    public JavaExecutableCLIBuilder internalVersionInfo() {
        this.arguments.add("-Xinternalversion");
        return this;
    }

    public JavaExecutableCLIBuilder configureLogging(String loggingOptions) {
        Objects.requireNonNull(loggingOptions, "Logging options cannot be null");
        this.arguments.add("-Xlog:" + loggingOptions);
        return this;
    }

    public JavaExecutableCLIBuilder configureLogging(LoggingConfiguration configuration) {
        Objects.requireNonNull(configuration, "Logging configuration cannot be null");
        return configureLogging(configuration.asArgument());
    }

    public static LoggingConfiguration loggingConfiguration() {
        return new LoggingConfiguration();
    }

    public JavaExecutableCLIBuilder mixedMode() {
        this.arguments.add("-Xmixed");
        return this;
    }

    public JavaExecutableCLIBuilder generationalMaxHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size cannot be null");
        this.arguments.add("-Xmn" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder minimumHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size cannot be null");
        this.arguments.add("-Xms" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maximumHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size cannot be null");
        this.arguments.add("-Xmx" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder disableClassGC() {
        this.arguments.add("-Xnoclassgc");
        return this;
    }

    public JavaExecutableCLIBuilder reduceSignalUsage() {
        this.arguments.add("-Xrs");
        return this;
    }

    public JavaExecutableCLIBuilder setClassDataSharingMode(AutoOnOff mode) {
        Objects.requireNonNull(mode, "Class Data Sharing mode cannot be null");
        this.arguments.add("-Xshare:class" + mode.getMode());
        return this;
    }

    public JavaExecutableCLIBuilder showSettings() {
        this.arguments.add("-XshowSettings");
        return this;
    }

    public JavaExecutableCLIBuilder showSettings(SettingCategory category) {
        Objects.requireNonNull(category, "Setting category cannot be null");
        if (category == SettingCategory.SYSTEM && !OperatingSystem.isLinux())
            throw new UnsupportedOperationException("Showing system settings is only supported on Linux systems.");

        this.arguments.add("-XshowSettings:" + category.getCategoryName());
        return this;
    }

    public JavaExecutableCLIBuilder threadStackSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Stack size unit cannot be null");
        this.arguments.add("-Xss" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder addReads(String sourceModule, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-reads " + sourceModule + "=" + String.join(",", targetModules));
        return this;
    }

    public JavaExecutableCLIBuilder addReadsAllUnnamed(String sourceModule) {
        return addReads(sourceModule, "ALL-UNNAMED");
    }

    public JavaExecutableCLIBuilder addExports(String sourceModule, String packageName, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-exports " + sourceModule + "/" + packageName + "=" + String.join(",", targetModules));
        return this;
    }

    public JavaExecutableCLIBuilder addExportsAllUnnamed(String sourceModule, String packageName) {
        return addExports(sourceModule, packageName, "ALL-UNNAMED");
    }

    public JavaExecutableCLIBuilder addOpens(String sourceModule, String packageName, String... targetModules) {
        Objects.requireNonNull(sourceModule, "Source module cannot be null");
        Objects.requireNonNull(packageName, "Package name cannot be null");
        Objects.requireNonNull(targetModules, "Target modules cannot be null");

        this.arguments.add("--add-opens " + sourceModule + "/" + packageName + "=" + String.join(",", targetModules));
        return this;
    }

    public JavaExecutableCLIBuilder addOpensAllUnnamed(String sourceModule, String packageName) {
        return addOpens(sourceModule, packageName, "ALL-UNNAMED");
    }

    public JavaExecutableCLIBuilder limitModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--limit-modules " + String.join(",", modules));
        return this;
    }

    public JavaExecutableCLIBuilder patchModule(String moduleName, String... patchPaths) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(patchPaths, "Patch paths cannot be null");
        this.arguments.add("--patch-module " + moduleName + "=" + String.join(File.pathSeparator, patchPaths));
        return this;
    }

    public JavaExecutableCLIBuilder sourceVersion(String version) {
        if (launchType != LaunchType.SOURCE_FILE)
            throw new UnsupportedOperationException("Setting source version is only supported when launching a source file.");

        Objects.requireNonNull(version, "Source version cannot be null");
        this.arguments.add("--source " + version);
        return this;
    }

    public JavaExecutableCLIBuilder sunMiscUnsafeMemoryAccess(AccessMode mode) {
        Objects.requireNonNull(mode, "Unsafe memory access mode cannot be null");
        this.arguments.add("--sun-misc-unsafe-memory-access=" + mode.getMode());
        return this;
    }

    public JavaExecutableCLIBuilder startOnFirstThread() {
        if (!OperatingSystem.isMac())
            throw new UnsupportedOperationException("Starting on first thread is only supported on macOS.");

        this.arguments.add("-XstartOnFirstThread");
        return this;
    }

    public JavaExecutableCLIBuilder dockName(String appName) {
        if (!OperatingSystem.isMac())
            throw new UnsupportedOperationException("Setting dock name is only supported on macOS.");

        Objects.requireNonNull(appName, "Application name cannot be null");
        this.arguments.add("-Xdock:name=" + appName);
        return this;
    }

    public JavaExecutableCLIBuilder dockIcon(Path iconPath) {
        if (!OperatingSystem.isMac())
            throw new UnsupportedOperationException("Setting dock icon is only supported on macOS.");

        Objects.requireNonNull(iconPath, "Icon path cannot be null");
        this.arguments.add("-Xdock:icon=" + iconPath);
        return this;
    }

    public JavaExecutableCLIBuilder unlockDiagnosticVMOptions() {
        this.arguments.add("-XX:+UnlockDiagnosticVMOptions");
        return this;
    }

    public JavaExecutableCLIBuilder unlockExperimentalVMOptions() {
        this.arguments.add("-XX:+UnlockExperimentalVMOptions");
        return this;
    }

    public JavaExecutableCLIBuilder activeProcessorCount(int count) {
        if (count <= 0)
            throw new IllegalArgumentException("Active processor count must be positive.");

        this.arguments.add("-XX:ActiveProcessorCount=" + count);
        return this;
    }

    public JavaExecutableCLIBuilder allocateHeapAt(Path path) {
        Objects.requireNonNull(path, "Heap allocation path cannot be null");
        this.arguments.add("-XX:AllocateHeapAt=" + path);
        return this;
    }

    public JavaExecutableCLIBuilder disableCompactStrings() {
        this.arguments.add("-XX:-CompactStrings");
        return this;
    }

    public JavaExecutableCLIBuilder errorFile(Path errorFilePath) {
        Objects.requireNonNull(errorFilePath, "Error file path cannot be null");
        this.arguments.add("-XX:ErrorFile=" + errorFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder enableExtensiveErrorReports() {
        this.arguments.add("-XX:+ExtensiveErrorReports");
        return this;
    }

    public JavaExecutableCLIBuilder flightRecorderOptions(FlightRecorderOption... options) {
        Objects.requireNonNull(options, "Flight recorder options cannot be null");
        List<String> optionStrings = new ArrayList<>();
        for (FlightRecorderOption option : options) {
            optionStrings.add(option.name() + "=" + option.value());
        }

        this.arguments.add("-XX:FlightRecorderOptions:" + String.join(",", optionStrings));
        return this;
    }

    public JavaExecutableCLIBuilder largePageSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Page size unit cannot be null");
        this.arguments.add("-XX:LargePageSizeInBytes=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxDirectMemorySize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Direct memory size unit cannot be null");
        this.arguments.add("-XX:MaxDirectMemorySize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder disableMaxFileDescriptorLimit() {
        this.arguments.add("-XX:-MaxFDLimit");
        return this;
    }

    public JavaExecutableCLIBuilder nativeMemoryTracking(NativeMemoryTracking tracking) {
        Objects.requireNonNull(tracking, "Native memory tracking mode cannot be null");
        this.arguments.add("-XX:NativeMemoryTracking=" + tracking.getState());
        return this;
    }

    public JavaExecutableCLIBuilder trimNativeHeapInterval(long interval, TimeUnit unit) {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("TrimNativeHeapInterval is only supported on Linux systems.");

        if (interval < 0)
            throw new IllegalArgumentException("Trim interval cannot be negative.");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.arguments.add("-XX:TrimNativeHeapInterval=" + unit.toMillis(interval));
        return this;
    }

    public JavaExecutableCLIBuilder enableClientVMEmulation() {
        this.arguments.add("-XX:+NeverActAsServerClassMachine");
        return this;
    }

    public JavaExecutableCLIBuilder objectAlignmentInBytes(int alignment) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0)
            throw new IllegalArgumentException("Object alignment must be a positive power of two.");

        if (alignment < 8 || alignment > 256)
            throw new IllegalArgumentException("Object alignment must be between 8 and 256 bytes.");

        this.arguments.add("-XX:ObjectAlignmentInBytes=" + alignment);
        return this;
    }

    public JavaExecutableCLIBuilder onError(String command) {
        Objects.requireNonNull(command, "OnError command cannot be null");
        this.arguments.add("-XX:OnError=\"" + command + "\"");
        return this;
    }

    public JavaExecutableCLIBuilder onOutOfMemoryError(String command) {
        Objects.requireNonNull(command, "OnOutOfMemoryError command cannot be null");
        this.arguments.add("-XX:OnOutOfMemoryError=\"" + command + "\"");
        return this;
    }

    public JavaExecutableCLIBuilder enablePrintingCommandLineFlags() {
        this.arguments.add("-XX:+PrintCommandLineFlags");
        return this;
    }

    public JavaExecutableCLIBuilder preserveFramePointer(boolean preserve) {
        this.arguments.add(preserve ? "-XX:+PreserveFramePointer" : "-XX:-PreserveFramePointer");
        return this;
    }

    public JavaExecutableCLIBuilder enablePrintingNMTStatistics() {
        this.arguments.add("-XX:+PrintNMTStatistics");
        return this;
    }

    public JavaExecutableCLIBuilder sharedArchiveFile(Path archivePath) {
        Objects.requireNonNull(archivePath, "Shared archive file path cannot be null");
        this.arguments.add("-XX:SharedArchiveFile=" + archivePath);
        return this;
    }

    public JavaExecutableCLIBuilder sharedArchiveFileDynamic(Path dynamicArchivePath) {
        Objects.requireNonNull(dynamicArchivePath, "Dynamic shared archive file path cannot be null");
        this.arguments.add("-XX:SharedArchiveFile=," + dynamicArchivePath);
        return this;
    }

    public JavaExecutableCLIBuilder sharedArchiveFile(Path staticArchivePath, Path dynamicArchivePath) {
        Objects.requireNonNull(staticArchivePath, "Static shared archive file path cannot be null");
        Objects.requireNonNull(dynamicArchivePath, "Dynamic shared archive file path cannot be null");
        this.arguments.add("-XX:SharedArchiveFile=" + staticArchivePath + File.pathSeparator + dynamicArchivePath);
        return this;
    }

    public JavaExecutableCLIBuilder verifySharedSpaces() {
        this.arguments.add("-XX:+VerifySharedSpaces");
        return this;
    }

    public JavaExecutableCLIBuilder sharedArchiveConfigFile(Path configFilePath) {
        Objects.requireNonNull(configFilePath, "Shared archive config file path cannot be null");
        this.arguments.add("-XX:SharedArchiveConfigFile=" + configFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder sharedClassListFile(Path classListFilePath) {
        Objects.requireNonNull(classListFilePath, "Shared class list file path cannot be null");
        this.arguments.add("-XX:SharedClassListFile=" + classListFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder showCodeDetailsInExceptionMessages() {
        this.arguments.add("-XX:+ShowCodeDetailsInExceptionMessages");
        return this;
    }

    public JavaExecutableCLIBuilder showMessageBoxOnError() {
        this.arguments.add("-XX:+ShowMessageBoxOnError");
        return this;
    }

    public JavaExecutableCLIBuilder startFlightRecording(FlightRecorderParameters parameters) {
        Objects.requireNonNull(parameters, "Flight recorder parameters cannot be null");
        this.arguments.add("-XX:StartFlightRecording=" + parameters);
        return this;
    }

    public JavaExecutableCLIBuilder threadStackSize(int sizeInKB) {
        if (sizeInKB <= 0)
            throw new IllegalArgumentException("Stack size must be positive.");

        this.arguments.add("-Xss" + sizeInKB + "K");
        return this;
    }

    public JavaExecutableCLIBuilder useCompactObjectHeaders() {
        if (jdk.version().major() < 25)
            throw new UnsupportedOperationException("Compact object headers are only supported in JDK 25 and above.");

        this.arguments.add("-XX:+UseCompactObjectHeaders");
        return this;
    }

    public JavaExecutableCLIBuilder disableCompressedPointers() {
        this.arguments.add("-XX:-UseCompressedOops");
        return this;
    }

    public JavaExecutableCLIBuilder disableContainerSupport() {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("Disabling container support is only supported on Linux systems.");

        this.arguments.add("-XX:-UseContainerSupport");
        return this;
    }

    public JavaExecutableCLIBuilder useLargePages() {
        this.arguments.add("-XX:+UseLargePages");
        return this;
    }

    public JavaExecutableCLIBuilder useTransparentHugePages() {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("UseTransparentHugePages is only supported on Linux systems.");

        this.arguments.add("-XX:+UseTransparentHugePages");
        return this;
    }

    public JavaExecutableCLIBuilder allowInstallingSignalHandlers() {
        if (OperatingSystem.isWindows())
            throw new UnsupportedOperationException("Installing signal handlers is not supported on Windows systems.");

        this.arguments.add("-XX:+AllowUserSignalHandlers");
        return this;
    }

    public JavaExecutableCLIBuilder vmOptionsFile(Path vmOptionsFilePath) {
        Objects.requireNonNull(vmOptionsFilePath, "VM options file path cannot be null");
        this.arguments.add("-XX:VMOptionsFile=" + vmOptionsFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder branchProtectionMode(BranchProtectionMode mode) {
        if (!OperatingSystem.isLinux())
            throw new UnsupportedOperationException("Branch protection mode is only supported on Linux (AArch64) systems.");

        Objects.requireNonNull(mode, "Branch protection mode cannot be null");
        this.arguments.add("-XX:BranchProtection=" + mode.getMode());
        return this;
    }

    public JavaExecutableCLIBuilder allocateInstancePrefetchLines(int lineCount) {
        if (lineCount < 0)
            throw new IllegalArgumentException("Line count cannot be negative.");

        this.arguments.add("-XX:AllocateInstancePrefetchLines=" + lineCount);
        return this;
    }

    public JavaExecutableCLIBuilder allocatePrefetchDistance(long distance, ByteUnit unit) {
        Objects.requireNonNull(unit, "Prefetch distance unit cannot be null");
        if (distance < -1)
            throw new IllegalArgumentException("Prefetch distance cannot be negative.");

        this.arguments.add("-XX:AllocatePrefetchDistance=" + distance + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder allocatePrefetchInstruction(byte instructionType) {
        if (instructionType < 0 || instructionType > 3)
            throw new IllegalArgumentException("Instruction type must be 0, 1, 2, or 3.");

        this.arguments.add("-XX:AllocatePrefetchInstr=" + instructionType);
        return this;
    }

    public JavaExecutableCLIBuilder allocatePrefetchLines(int lineCount) {
        if (lineCount < 0)
            throw new IllegalArgumentException("Line count cannot be negative.");

        this.arguments.add("-XX:AllocatePrefetchLines=" + lineCount);
        return this;
    }

    public JavaExecutableCLIBuilder allocatePrefetchStepSize(int stepSize, ByteUnit unit) {
        Objects.requireNonNull(unit, "Step size unit cannot be null");
        if (stepSize < 0)
            throw new IllegalArgumentException("Step size cannot be negative.");

        this.arguments.add("-XX:AllocatePrefetchStepSize=" + stepSize + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder allocatePrefetchStyle(PrefetchStyle style) {
        Objects.requireNonNull(style, "Prefetch style cannot be null");
        this.arguments.add("-XX:AllocatePrefetchStyle=" + style.asInt());
        return this;
    }

    public JavaExecutableCLIBuilder enableBackgroundCompilation() {
        this.arguments.add("-XX:+BackgroundCompilation");
        return this;
    }

    public JavaExecutableCLIBuilder compilerThreadsForCompilation(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("Thread count must be positive.");

        this.arguments.add("-XX:CompilerThreadsForCompilation=" + threadCount);
        return this;
    }

    public JavaExecutableCLIBuilder useDynamicNumberOfCompilerThreads() {
        this.arguments.add("-XX:+UseDynamicNumberOfCompilerThreads");
        return this;
    }

    public JavaExecutableCLIBuilder compileCommand(CompileCommand command, String... methodSpecs) {
        Objects.requireNonNull(command, "Compile command cannot be null");
        Objects.requireNonNull(methodSpecs, "Method specifications cannot be null");

        this.arguments.add("-XX:CompileCommand=\"" + command.getCommand() + "," + String.join(",", methodSpecs) + "\"");
        return this;
    }

    public JavaExecutableCLIBuilder compileCommandFile(Path commandFilePath) {
        Objects.requireNonNull(commandFilePath, "Command file path cannot be null");
        this.arguments.add("-XX:CompileCommandFile=" + commandFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder compilerDirectivesFile(Path directivesFilePath) {
        Objects.requireNonNull(directivesFilePath, "Directives file path cannot be null");
        this.arguments.add("-XX:CompilerDirectivesFile=" + directivesFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder shouldPrintCompilerDirectives() {
        this.arguments.add("-XX:+PrintCompilerDirectives");
        return this;
    }

    public JavaExecutableCLIBuilder compileOnly(String... methodSpecs) {
        Objects.requireNonNull(methodSpecs, "Method specifications cannot be null");

        this.arguments.add("-XX:CompileOnly=" + String.join(",", methodSpecs));
        return this;
    }

    public JavaExecutableCLIBuilder compileThresholdScale(int scale) {
        if (scale <= 0)
            throw new IllegalArgumentException("Scale must be positive.");

        this.arguments.add("-XX:CompileThresholdScale=" + scale);
        return this;
    }

    public JavaExecutableCLIBuilder enableEscapeAnalysis(boolean enable) {
        this.arguments.add(enable ? "-XX:+DoEscapeAnalysis" : "-XX:-DoEscapeAnalysis");
        return this;
    }

    public JavaExecutableCLIBuilder initialCodeCacheSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code cache size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Initial code cache size cannot be negative.");

        this.arguments.add("-XX:InitialCodeCacheSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder enableMethodInlining(boolean enable) {
        this.arguments.add(enable ? "-XX:+Inline" : "-XX:-Inline");
        return this;
    }

    public JavaExecutableCLIBuilder inlineSmallCode(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Inline small code size cannot be negative.");

        this.arguments.add("-XX:InlineSmallCode=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder enableCompilationLogging() {
        this.arguments.add("-XX:+LogCompilation");
        return this;
    }

    public JavaExecutableCLIBuilder hotMethodInlineSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Hot method inline size cannot be negative.");

        this.arguments.add("-XX:FreqInlineSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxInlineSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max inline size cannot be negative.");

        this.arguments.add("-XX:MaxInlineSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder c1MaxInlineSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("C1 max inline size cannot be negative.");

        this.arguments.add("-XX:C1MaxInlineSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxTrivialSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max trivial size cannot be negative.");

        this.arguments.add("-XX:MaxTrivialSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder c1MaxTrivialSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("C1 max trivial size cannot be negative.");

        this.arguments.add("-XX:C1MaxTrivialSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxNodeLimit(int limit) {
        if (limit < 0)
            throw new IllegalArgumentException("Max node limit cannot be negative.");

        this.arguments.add("-XX:MaxNodeLimit=" + limit);
        return this;
    }

    public JavaExecutableCLIBuilder nonmethodCodeHeapSize(long sizeInBytes) {
        if (sizeInBytes < 0)
            throw new IllegalArgumentException("Non-method code heap size cannot be negative.");

        this.arguments.add("-XX:NonNMethodCodeHeapSize=" + sizeInBytes);
        return this;
    }

    public JavaExecutableCLIBuilder nonprofiledCodeHeapSize(long sizeInBytes) {
        if (sizeInBytes < 0)
            throw new IllegalArgumentException("Non-profiled code heap size cannot be negative.");

        this.arguments.add("-XX:NonProfiledCodeHeapSize=" + sizeInBytes);
        return this;
    }

    public JavaExecutableCLIBuilder enableOptimizingStringConcat(boolean enable) {
        this.arguments.add(enable ? "-XX:+OptimizeStringConcat" : "-XX:-OptimizeStringConcat");
        return this;
    }

    public JavaExecutableCLIBuilder enablePrintingAssemblyCode() {
        this.arguments.add("-XX:+PrintAssembly");
        return this;
    }

    public JavaExecutableCLIBuilder profiledCodeHeapSize(long sizeInBytes) {
        if (sizeInBytes < 0)
            throw new IllegalArgumentException("Profiled code heap size cannot be negative.");

        this.arguments.add("-XX:ProfiledCodeHeapSize=" + sizeInBytes);
        return this;
    }

    public JavaExecutableCLIBuilder enableMethodCompilationPrinting() {
        this.arguments.add("-XX:+PrintCompilation");
        return this;
    }

    public JavaExecutableCLIBuilder enableInliningInfoPrinting() {
        this.arguments.add("-XX:+PrintInlining");
        return this;
    }

    public JavaExecutableCLIBuilder reserveCodeCacheSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Code cache size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Reserved code cache size cannot be negative.");

        if (unit.toBytes(size) > ByteUnit.GIGABYTES.toBytes(2))
            throw new IllegalArgumentException("Reserved code cache size cannot exceed 2 GB.");

        this.arguments.add("-XX:ReservedCodeCacheSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder enableSegmentedCodeCache() {
        this.arguments.add("-XX:+SegmentedCodeCache");
        return this;
    }

    public JavaExecutableCLIBuilder startAggressiveSweepingAt(int percentage) {
        if (percentage < 0 || percentage > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");

        this.arguments.add("-XX:AggressiveHeapSweepingAt=" + percentage);
        return this;
    }

    public JavaExecutableCLIBuilder disableTieredCompilation() {
        this.arguments.add("-XX:-TieredCompilation");
        return this;
    }

    public JavaExecutableCLIBuilder sseInstructionSetVersion(String version) {
        Objects.requireNonNull(version, "SSE instruction set version cannot be null");
        this.arguments.add("-XX:UseSSE=" + version);
        return this;
    }

    public JavaExecutableCLIBuilder avxInstructionSetVersion(String version) {
        Objects.requireNonNull(version, "AVX instruction set version cannot be null");
        this.arguments.add("-XX:UseAVX=" + version);
        return this;
    }

    public JavaExecutableCLIBuilder enableAES(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAES" : "-XX:-UseAES");
        return this;
    }

    public JavaExecutableCLIBuilder enableAESIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAESIntrinsics" : "-XX:-UseAESIntrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableAESCTRIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAESCTRIntrinsics" : "-XX:-UseAESCTRIntrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableGHASHIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseGHASHIntrinsics" : "-XX:-UseGHASHIntrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableChaCha20Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseChaCha20Intrinsics" : "-XX:-UseChaCha20Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enablePoly1305Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UsePoly1305Intrinsics" : "-XX:-UsePoly1305Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableBASE64Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseBASE64Intrinsics" : "-XX:-UseBASE64Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableAdler32Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAdler32Intrinsics" : "-XX:-UseAdler32Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableCRC32Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCRC32Intrinsics" : "-XX:-UseCRC32Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableCRC32CIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCRC32CIntrinsics" : "-XX:-UseCRC32CIntrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableSHA(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA" : "-XX:-UseSHA");
        return this;
    }

    public JavaExecutableCLIBuilder enableSHA1Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA1Intrinsics" : "-XX:-UseSHA1Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableSHA256Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA256Intrinsics" : "-XX:-UseSHA256Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableSHA512Intrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSHA512Intrinsics" : "-XX:-UseSHA512Intrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableMathExactIntrinsics(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMathExactIntrinsics" : "-XX:-UseMathExactIntrinsics");
        return this;
    }

    public JavaExecutableCLIBuilder enableMultiplyToLenIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMultiplyToLenIntrinsic" : "-XX:-UseMultiplyToLenIntrinsic");
        return this;
    }

    public JavaExecutableCLIBuilder enableSquareToLenIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSquareToLenIntrinsic" : "-XX:-UseSquareToLenIntrinsic");
        return this;
    }

    public JavaExecutableCLIBuilder enableMulAddIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMulAddIntrinsic" : "-XX:-UseMulAddIntrinsic");
        return this;
    }

    public JavaExecutableCLIBuilder enableMontgomeryMultiplyIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMontgomeryMultiplyIntrinsic" : "-XX:-UseMontgomeryMultiplyIntrinsic");
        return this;
    }

    public JavaExecutableCLIBuilder enableMontgomerySquareIntrinsic(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseMontgomerySquareIntrinsic" : "-XX:-UseMontgomerySquareIntrinsic");
        return this;
    }

    // -XX:+UseCMoveUnconditionally
    //Generates CMove (scalar and vector) instructions regardless of profitability analysis.
    //-XX:+UseCodeCacheFlushing
    //Enables flushing of the code cache before shutting down the compiler. This option is enabled by default. To disable flushing of the code cache before shutting down the compiler, specify -XX:-UseCodeCacheFlushing.
    //-XX:+UseCondCardMark
    //Enables checking if the card is already marked before updating the card table. This option is disabled by default. It should be used only on machines with multiple sockets, where it increases the performance of Java applications that rely on concurrent operations.
    //-XX:+UseCountedLoopSafepoints
    //Keeps safepoints in counted loops. Its default value depends on whether the selected garbage collector requires low latency safepoints.
    //-XX:LoopStripMiningIter=number_of_iterations
    //Controls the number of iterations in the inner strip mined loop. Strip mining transforms counted loops into two level nested loops. Safepoints are kept in the outer loop while the inner loop can execute at full speed. This option controls the maximum number of iterations in the inner loop. The default value is 1,000.
    //-XX:LoopStripMiningIterShortLoop=number_of_iterations
    //Controls loop strip mining optimization. Loops with the number of iterations less than specified will not have safepoints in them. Default value is 1/10th of -XX:LoopStripMiningIter.
    //-XX:+UseFMA
    //Enables hardware-based FMA intrinsics for hardware where FMA instructions are available (such as, Intel and ARM64). FMA intrinsics are generated for the java.lang.Math.fma(a, b, c) methods that calculate the value of ( a * b + c ) expressions.
    //-XX:+UseSuperWord
    //Enables the transformation of scalar operations into superword operations. Superword is a vectorization optimization. This option is enabled by default. To disable the transformation of scalar operations into superword operations, specify -XX:-UseSuperWord.

    public JavaExecutableCLIBuilder enableCMoveUnconditionally(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCMoveUnconditionally" : "-XX:-UseCMoveUnconditionally");
        return this;
    }

    public JavaExecutableCLIBuilder enableCodeCacheFlushing(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCodeCacheFlushing" : "-XX:-UseCodeCacheFlushing");
        return this;
    }

    public JavaExecutableCLIBuilder enableCondCardMark(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCondCardMark" : "-XX:-UseCondCardMark");
        return this;
    }

    public JavaExecutableCLIBuilder enableCountedLoopSafepoints(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseCountedLoopSafepoints" : "-XX:-UseCountedLoopSafepoints");
        return this;
    }

    public JavaExecutableCLIBuilder loopStripMiningIterations(int iterationCount) {
        if (iterationCount <= 0)
            throw new IllegalArgumentException("Iteration count must be positive.");

        this.arguments.add("-XX:LoopStripMiningIter=" + iterationCount);
        return this;
    }

    public JavaExecutableCLIBuilder loopStripMiningIterationsForShortLoops(int iterationCount) {
        if (iterationCount <= 0)
            throw new IllegalArgumentException("Iteration count must be positive.");

        this.arguments.add("-XX:LoopStripMiningIterShortLoop=" + iterationCount);
        return this;
    }

    public JavaExecutableCLIBuilder enableFMA(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseFMA" : "-XX:-UseFMA");
        return this;
    }

    public JavaExecutableCLIBuilder enableSuperWord(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseSuperWord" : "-XX:-UseSuperWord");
        return this;
    }

    public JavaExecutableCLIBuilder disableAttachMechanism() {
        this.arguments.add("-XX:+DisableAttachMechanism");
        return this;
    }

    public JavaExecutableCLIBuilder enableDTraceAllocProbes() {
        if (!OperatingSystem.isLinux() && !OperatingSystem.isMac())
            throw new UnsupportedOperationException("DTrace allocation probes are only supported on macOS and Linux systems.");

        this.arguments.add("-XX:+DTraceAllocProbes");
        return this;
    }

    public JavaExecutableCLIBuilder enableDTraceMethodProbes() {
        if (!OperatingSystem.isLinux() && !OperatingSystem.isMac())
            throw new UnsupportedOperationException("DTrace method probes are only supported on macOS and Linux systems.");

        this.arguments.add("-XX:+DTraceMethodProbes");
        return this;
    }

    public JavaExecutableCLIBuilder enableDTraceMonitorProbes() {
        if (!OperatingSystem.isLinux() && !OperatingSystem.isMac())
            throw new UnsupportedOperationException("DTrace monitor probes are only supported on macOS and Linux systems.");

        this.arguments.add("-XX:+DTraceMonitorProbes");
        return this;
    }

    public JavaExecutableCLIBuilder enableDumpingHeapOnOutOfMemoryError() {
        this.arguments.add("-XX:+HeapDumpOnOutOfMemoryError");
        return this;
    }

    public JavaExecutableCLIBuilder heapDumpPath(Path dumpPath) {
        Objects.requireNonNull(dumpPath, "Heap dump path cannot be null");
        this.arguments.add("-XX:HeapDumpPath=" + dumpPath);
        return this;
    }

    public JavaExecutableCLIBuilder logFile(Path logFilePath) {
        Objects.requireNonNull(logFilePath, "Log file path cannot be null");
        this.arguments.add("-XX:LogFile=" + logFilePath);
        return this;
    }

    public JavaExecutableCLIBuilder enablePrintingClassHistogram() {
        this.arguments.add("-XX:+PrintClassHistogram");
        return this;
    }

    public JavaExecutableCLIBuilder printConcurrentLocks() {
        this.arguments.add("-XX:+PrintConcurrentLocks");
        return this;
    }

    public JavaExecutableCLIBuilder printFlagRanges() {
        this.arguments.add("-XX:+PrintFlagRanges");
        return this;
    }

    public JavaExecutableCLIBuilder perfDataSaveToFile() {
        this.arguments.add("-XX:+PerfDataSaveToFile");
        return this;
    }

    public JavaExecutableCLIBuilder enableUsingPerfData(boolean enable) {
        this.arguments.add(enable ? "-XX:+UsePerfData" : "-XX:-UsePerfData");
        return this;
    }

    public JavaExecutableCLIBuilder enableAggressiveHeap() {
        this.arguments.add("-XX:+AggressiveHeap");
        return this;
    }

    public JavaExecutableCLIBuilder alwaysPreTouch() {
        this.arguments.add("-XX:+AlwaysPreTouch");
        return this;
    }

    public JavaExecutableCLIBuilder concurrentGCThreads(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("Thread count must be positive.");

        this.arguments.add("-XX:ConcGCThreads=" + threadCount);
        return this;
    }

    public JavaExecutableCLIBuilder disableExplicitGC() {
        this.arguments.add("-XX:+DisableExplicitGC");
        return this;
    }

    public JavaExecutableCLIBuilder enableConcurrentExplicitGCInvokes() {
        this.arguments.add("-XX:+ExplicitGCInvokesConcurrent");
        return this;
    }

    public JavaExecutableCLIBuilder G1AdaptiveIHOPNumInitialSamples(int sampleCount) {
        if (sampleCount < 0)
            throw new IllegalArgumentException("Sample count cannot be negative.");

        this.arguments.add("-XX:G1AdaptiveIHOPNumInitialSamples=" + sampleCount);
        return this;
    }

    public JavaExecutableCLIBuilder G1HeapRegionSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap region size unit cannot be null");

        if (unit.toBytes(size) < ByteUnit.MEGABYTES.toBytes(1) || unit.toBytes(size) > ByteUnit.MEGABYTES.toBytes(32))
            throw new IllegalArgumentException("G1 heap region size must be between 1 MB and 32 MB.");

        this.arguments.add("-XX:G1HeapRegionSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder G1HeapWastePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Heap waste percent must be between 0 and 100.");

        this.arguments.add("-XX:G1HeapWastePercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder G1MaxNewSizePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max new size percent must be between 0 and 100.");

        this.arguments.add("-XX:G1MaxNewSizePercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder G1MixedGCCountTarget(int targetCount) {
        if (targetCount < 0)
            throw new IllegalArgumentException("Target count cannot be negative.");

        this.arguments.add("-XX:G1MixedGCCountTarget=" + targetCount);
        return this;
    }

    public JavaExecutableCLIBuilder G1MixedGCLiveThresholdPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Live threshold percent must be between 0 and 100.");

        this.arguments.add("-XX:G1MixedGCLiveThresholdPercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder G1NewSizePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("New size percent must be between 0 and 100.");

        this.arguments.add("-XX:G1NewSizePercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder G1OldCSetRegionThresholdPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Old CSet region threshold percent must be between 0 and 100.");

        this.arguments.add("-XX:G1OldCSetRegionThresholdPercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder G1ReservePercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Reserve percent must be between 0 and 100.");

        this.arguments.add("-XX:G1ReservePercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder enableG1AdaptiveIHOP() {
        this.arguments.add("-XX:+G1UseAdaptiveIHOP");
        return this;
    }

    public JavaExecutableCLIBuilder initialHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Initial heap size cannot be negative.");

        if (unit.toBytes(size) < ByteUnit.MEGABYTES.toBytes(1) || (unit.toBytes(size) % 1024) != 0)
            throw new IllegalArgumentException("Initial heap size must be at least 1 MB and a multiple of 1024 bytes.");

        this.arguments.add("-XX:InitialHeapSize" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder initialSurvivorRatio(int ratio) {
        if (ratio <= 0)
            throw new IllegalArgumentException("Initial survivor ratio must be positive.");

        this.arguments.add("-XX:InitialSurvivorRatio=" + ratio);
        return this;
    }

    public JavaExecutableCLIBuilder initiatingHeapOccupancyPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Initiating heap occupancy percent must be between 0 and 100.");

        this.arguments.add("-XX:InitiatingHeapOccupancyPercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder maxGCPause(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Max GC pause time cannot be negative.");

        this.arguments.add("-XX:MaxGCPauseMillis=" + unit.toMillis(time));
        return this;
    }

    public JavaExecutableCLIBuilder maxHeapSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Heap size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max heap size cannot be negative.");

        if (unit.toBytes(size) < ByteUnit.MEGABYTES.toBytes(2) || (unit.toBytes(size) % 1024) != 0)
            throw new IllegalArgumentException("Max heap size must be at least 2 MB and a multiple of 1024 bytes.");

        this.arguments.add("-XX:MaxHeapSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxHeapFreeRatioPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max heap free ratio percent must be between 0 and 100.");

        this.arguments.add("-XX:MaxHeapFreeRatio=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder maxMetaspaceSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Metaspace size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max metaspace size cannot be negative.");

        this.arguments.add("-XX:MaxMetaspaceSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxNewSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "New size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max new size cannot be negative.");

        this.arguments.add("-XX:MaxNewSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxRAM(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "RAM size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Max RAM size cannot be negative.");

        if (unit.toBytes(size) > ByteUnit.GIGABYTES.toBytes(128))
            throw new IllegalArgumentException("Max RAM size cannot exceed 128 GB.");

        this.arguments.add("-XX:MaxRAM=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder maxRAMPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max RAM percent must be between 0 and 100.");

        this.arguments.add("-XX:MaxRAMPercent=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder maxTenuringThreshold(int threshold) {
        if (threshold < 0)
            throw new IllegalArgumentException("Max tenuring threshold cannot be negative.");

        if (threshold > 15)
            throw new IllegalArgumentException("Max tenuring threshold cannot exceed 15.");

        this.arguments.add("-XX:MaxTenuringThreshold=" + threshold);
        return this;
    }

    public JavaExecutableCLIBuilder metaspaceSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "Metaspace size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("Metaspace size cannot be negative.");

        this.arguments.add("-XX:MetaspaceSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder minHeapFreeRatioPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Min heap free ratio percent must be between 0 and 100.");

        this.arguments.add("-XX:MinHeapFreeRatio=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder newRatio(int ratio) {
        if (ratio <= 0)
            throw new IllegalArgumentException("New ratio must be positive.");

        this.arguments.add("-XX:NewRatio=" + ratio);
        return this;
    }

    public JavaExecutableCLIBuilder newSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "New size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("New size cannot be negative.");

        this.arguments.add("-XX:NewSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder parallelGCThreads(int threadCount) {
        if (threadCount <= 0)
            throw new IllegalArgumentException("Thread count must be positive.");

        this.arguments.add("-XX:ParallelGCThreads=" + threadCount);
        return this;
    }

    public JavaExecutableCLIBuilder enableParallelRefProc(boolean enable) {
        this.arguments.add(enable ? "-XX:+ParallelRefProcEnabled" : "-XX:-ParallelRefProcEnabled");
        return this;
    }

    public JavaExecutableCLIBuilder enablePrintingAdaptiveSizePolicy() {
        this.arguments.add("-XX:+PrintAdaptiveSizePolicy");
        return this;
    }

    public JavaExecutableCLIBuilder softRefLRUPolicyMSPerMB(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Time cannot be negative.");

        this.arguments.add("-XX:SoftRefLRUPolicyMSPerMB=" + unit.toMillis(time));
        return this;
    }

    public JavaExecutableCLIBuilder incrementallyReduceJavaHeapSize() {
        this.arguments.add("-XX:-ShrinkHeapInSteps");
        return this;
    }

    public JavaExecutableCLIBuilder stringDeduplicationAgeThreshold(int threshold) {
        if (threshold < 0)
            throw new IllegalArgumentException("String deduplication age threshold cannot be negative.");

        this.arguments.add("-XX:StringDeduplicationAgeThreshold=" + threshold);
        return this;
    }

    public JavaExecutableCLIBuilder survivorRatio(int ratio) {
        if (ratio <= 0)
            throw new IllegalArgumentException("Survivor ratio must be positive.");

        this.arguments.add("-XX:SurvivorRatio=" + ratio);
        return this;
    }

    public JavaExecutableCLIBuilder targetSurvivorRatioPercent(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Target survivor ratio percent must be between 0 and 100.");

        this.arguments.add("-XX:TargetSurvivorRatio=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder TLABSize(long size, ByteUnit unit) {
        Objects.requireNonNull(unit, "TLAB size unit cannot be null");

        if (size < 0)
            throw new IllegalArgumentException("TLAB size cannot be negative.");

        this.arguments.add("-XX:TLABSize=" + size + unit.getUnit());
        return this;
    }

    public JavaExecutableCLIBuilder enableAdaptiveSizePolicy(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseAdaptiveSizePolicy" : "-XX:-UseAdaptiveSizePolicy");
        return this;
    }

    public JavaExecutableCLIBuilder enableG1GC() {
        this.arguments.add("-XX:+UseG1GC");
        return this;
    }

    public JavaExecutableCLIBuilder enableGCOverheadLimit() {
        this.arguments.add("-XX:+UseGCOverheadLimit");
        return this;
    }

    public JavaExecutableCLIBuilder enableNUMA() {
        this.arguments.add("-XX:+UseNUMA");
        return this;
    }

    public JavaExecutableCLIBuilder enableParallelGC() {
        this.arguments.add("-XX:+UseParallelGC");
        return this;
    }

    public JavaExecutableCLIBuilder enableSerialGC() {
        this.arguments.add("-XX:+UseSerialGC");
        return this;
    }

    public JavaExecutableCLIBuilder enableStringDeduplication(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseStringDeduplication" : "-XX:-UseStringDeduplication");
        return this;
    }

    public JavaExecutableCLIBuilder enableTLAB(boolean enable) {
        this.arguments.add(enable ? "-XX:+UseTLAB" : "-XX:-UseTLAB");
        return this;
    }

    public JavaExecutableCLIBuilder enableZGC() {
        this.arguments.add("-XX:+UseZGC");
        return this;
    }

    public JavaExecutableCLIBuilder allocationSpikeToleranceForZGC(float tolerance) {
        this.arguments.add("-XX:ZAllocationSpikeTolerance=" + tolerance);
        return this;
    }

    public JavaExecutableCLIBuilder maxCollectionIntervalForZGC(long time, TimeUnit unit) {
        Objects.requireNonNull(unit, "Time unit cannot be null");

        if (time < 0)
            throw new IllegalArgumentException("Max collection interval cannot be negative.");

        this.arguments.add("-XX:ZCollectionInterval=" + unit.toSeconds(time));
        return this;
    }

    public JavaExecutableCLIBuilder maxFragmentationLimitPercentForZGC(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Max fragmentation limit percent must be between 0 and 100.");

        this.arguments.add("-XX:ZFragmentationLimit=" + percent);
        return this;
    }

    public JavaExecutableCLIBuilder enableProactiveGCCyclesForZGC(boolean enable) {
        this.arguments.add(enable ? "-XX:+ZProactiveGCCycles" : "-XX:-ZProactiveGCCycles");
        return this;
    }

    public JavaExecutableCLIBuilder enableUncommitForZGC(boolean enable) {
        this.arguments.add(enable ? "-XX:+ZUncommit" : "-XX:-ZUncommit");
        return this;
    }

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

    private static String resolveExecutableName(boolean enableConsole) {
        if (OperatingSystem.isWindows() && !enableConsole)
            return "javaw.exe";

        return JDKManager.JAVA_EXECUTABLE_NAME;
    }

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

    @Getter
    public enum EnabledDisabled {
        ENABLED,
        DISABLED;

        private final String state;

        EnabledDisabled() {
            this.state = name().toLowerCase(Locale.ROOT);
        }
    }

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

            if (hasOutput || hasDecorators || hasOutputOptions) {
                if (builder.length() > 0)
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
