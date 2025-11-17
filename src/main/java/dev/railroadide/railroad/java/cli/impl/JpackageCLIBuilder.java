package dev.railroadide.railroad.java.cli.impl;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.cli.CLIBuilder;
import dev.railroadide.railroad.java.cli.ProcessExecution;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JpackageCLIBuilder implements CLIBuilder<Process, JpackageCLIBuilder> {
    private static final String EXECUTABLE_NAME = OperatingSystem.isWindows() ? "jpackage.exe" : "jpackage";

    private final JDK jdk;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> environmentVariables = new HashMap<>();
    private Path workingDirectory;
    private boolean useSystemEnvVars = true;
    private long timeoutDuration = 0;
    private TimeUnit timeoutUnit = TimeUnit.SECONDS;

    private JpackageCLIBuilder(JDK jdk) {
        this.jdk = Objects.requireNonNull(jdk, "JDK cannot be null");
    }

    /**
     * Creates a fresh builder tied to the provided {@link JDK}.
     *
     * @param jdk the JDK whose tools should be used
     * @return a new builder instance
     */
    public static JpackageCLIBuilder create(JDK jdk) {
        return new JpackageCLIBuilder(jdk);
    }

    @Override
    public JpackageCLIBuilder addArgument(String arg) {
        Objects.requireNonNull(arg, "Argument cannot be null");
        this.arguments.add(arg);
        return this;
    }

    @Override
    public JpackageCLIBuilder setWorkingDirectory(Path path) {
        this.workingDirectory = path;
        return this;
    }

    @Override
    public JpackageCLIBuilder setEnvironmentVariable(String key, String value) {
        Objects.requireNonNull(key, "Environment variable key cannot be null");
        Objects.requireNonNull(value, "Environment variable value cannot be null");
        this.environmentVariables.put(key, value);
        return this;
    }

    @Override
    public JpackageCLIBuilder useSystemEnvironmentVariables(boolean useSystemVars) {
        this.useSystemEnvVars = useSystemVars;
        return this;
    }

    @Override
    public JpackageCLIBuilder setTimeout(long duration, TimeUnit unit) {
        if (duration < 0)
            throw new IllegalArgumentException("Timeout duration cannot be negative");

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        this.timeoutDuration = duration;
        this.timeoutUnit = unit;
        return this;
    }

    /**
     * Instructs {@code jpackage} to read arguments from the given option file.
     *
     * @param optionFile path to the option file
     * @return this builder
     */
    public JpackageCLIBuilder optionFile(Path optionFile) {
        Objects.requireNonNull(optionFile, "Option file cannot be null");
        this.arguments.add("@" + optionFile);
        return this;
    }

    /**
     * Sets the target package type.
     *
     * @param type package type (e.g., {@code pkg}, {@code exe})
     * @return this builder
     */
    public JpackageCLIBuilder packageType(String type) {
        Objects.requireNonNull(type, "Package type cannot be null");
        this.arguments.add("--type " + type);
        return this;
    }

    /**
     * Declares the application version passed to {@code jpackage}.
     *
     * @param version version string
     * @return this builder
     */
    public JpackageCLIBuilder appVersion(String version) {
        Objects.requireNonNull(version, "Version cannot be null");
        this.arguments.add("--app-version " + version);
        return this;
    }

    /**
     * Sets the copyright notice inside the generated package.
     *
     * @param copyright copyright text
     * @return this builder
     */
    public JpackageCLIBuilder copyright(String copyright) {
        Objects.requireNonNull(copyright, "Copyright cannot be null");
        this.arguments.add("--copyright " + copyright);
        return this;
    }

    /**
     * Provides a description for the packaged application.
     *
     * @param description description text
     * @return this builder
     */
    public JpackageCLIBuilder description(String description) {
        Objects.requireNonNull(description, "Description cannot be null");
        this.arguments.add("--description " + description);
        return this;
    }

    /**
     * Requests {@code --help} output from {@code jpackage}.
     *
     * @return this builder
     */
    public JpackageCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    /**
     * Includes an icon for the application bundle.
     *
     * @param iconPath path to the icon file
     * @return this builder
     */
    public JpackageCLIBuilder icon(Path iconPath) {
        Objects.requireNonNull(iconPath, "Icon path cannot be null");
        this.arguments.add("--icon " + iconPath);
        return this;
    }

    /**
     * Names the generated application.
     *
     * @param name application name
     * @return this builder
     */
    public JpackageCLIBuilder applicationName(String name) {
        Objects.requireNonNull(name, "Application name cannot be null");
        this.arguments.add("--name " + name);
        return this;
    }

    /**
     * Configures the output directory for the package.
     *
     * @param destination destination directory
     * @return this builder
     */
    public JpackageCLIBuilder destination(Path destination) {
        Objects.requireNonNull(destination, "Destination cannot be null");
        this.arguments.add("--dest " + destination);
        return this;
    }

    /**
     * Points {@code jpackage} at extra resource files.
     *
     * @param resourceDir path to the resource directory
     * @return this builder
     */
    public JpackageCLIBuilder resourceDirectory(Path resourceDir) {
        Objects.requireNonNull(resourceDir, "Resource directory cannot be null");
        this.arguments.add("--resource-dir " + resourceDir);
        return this;
    }

    /**
     * Defines a temporary directory to use during packaging.
     *
     * @param tempDir temp directory
     * @return this builder
     */
    public JpackageCLIBuilder tempDirectory(Path tempDir) {
        Objects.requireNonNull(tempDir, "Temp directory cannot be null");
        this.arguments.add("--temp " + tempDir);
        return this;
    }

    /**
     * Adds vendor metadata to the package.
     *
     * @param vendor vendor name
     * @return this builder
     */
    public JpackageCLIBuilder vendor(String vendor) {
        Objects.requireNonNull(vendor, "Vendor cannot be null");
        this.arguments.add("--vendor " + vendor);
        return this;
    }

    /**
     * Enables verbose logging for {@code jpackage}.
     *
     * @return this builder
     */
    public JpackageCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    /**
     * Adds {@code --version} to the CLI arguments.
     *
     * @return this builder
     */
    public JpackageCLIBuilder versionInfo() {
        this.arguments.add("--version");
        return this;
    }

    /**
     * Passes module names to the launcher.
     *
     * @param modules module names
     * @return this builder
     */
    public JpackageCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    /**
     * Sets module path entries via string representations.
     *
     * @param modulePaths module path elements
     * @return this builder
     */
    public JpackageCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    /**
     * Sets module path entries via {@link Path} instances.
     *
     * @param modulePaths module path elements
     * @return this builder
     */
    public JpackageCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        return modulePath(Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new));
    }

    /**
     * Forwards arguments to the bundled {@code jlink} step.
     *
     * @param options jlink options
     * @return this builder
     */
    public JpackageCLIBuilder jlinkOptions(String... options) {
        Objects.requireNonNull(options, "jlink options cannot be null");
        this.arguments.add("--jlink-options " + String.join(" ", options));
        return this;
    }

    /**
     * Points {@code jpackage} at a custom runtime image.
     *
     * @param runtimeImage runtime image path
     * @return this builder
     */
    public JpackageCLIBuilder runtimeImage(Path runtimeImage) {
        Objects.requireNonNull(runtimeImage, "Runtime image path cannot be null");
        this.arguments.add("--runtime-image " + runtimeImage);
        return this;
    }

    /**
     * Sets the input directory containing application files.
     *
     * @param inputDirectory input folder
     * @return this builder
     */
    public JpackageCLIBuilder input(Path inputDirectory) {
        Objects.requireNonNull(inputDirectory, "Input directory cannot be null");
        this.arguments.add("--input " + inputDirectory);
        return this;
    }

    /**
     * Adds additional application content directories.
     *
     * @param contentPaths content paths
     * @return this builder
     */
    public JpackageCLIBuilder appContent(String... contentPaths) {
        Objects.requireNonNull(contentPaths, "App content paths cannot be null");
        this.arguments.add("--app-content " + String.join(",", contentPaths));
        return this;
    }

    /**
     * Declares an extra launcher with its properties file.
     *
     * @param name           launcher name
     * @param propertiesFile launcher properties path
     * @return this builder
     */
    public JpackageCLIBuilder addLauncher(String name, Path propertiesFile) {
        Objects.requireNonNull(name, "Launcher name cannot be null");
        Objects.requireNonNull(propertiesFile, "Launcher properties files cannot be null");
        this.arguments.add("--add-launcher " + name + "=" + propertiesFile);
        return this;
    }

    /**
     * Provides launcher-specific arguments to include in the bundle metadata.
     *
     * @param arguments launcher arguments
     * @return this builder
     */
    public JpackageCLIBuilder launcherArguments(String arguments) {
        Objects.requireNonNull(arguments, "Launcher arguments cannot be null");
        this.arguments.add("--arguments " + arguments);
        return this;
    }

    /**
     * Supplies JVM options for the launcher.
     *
     * @param options JVM options string
     * @return this builder
     */
    public JpackageCLIBuilder launcherJavaOptions(String options) {
        Objects.requireNonNull(options, "Java options cannot be null");
        this.arguments.add("--java-options " + options);
        return this;
    }

    /**
     * Provides the fully qualified main class name.
     *
     * @param mainClass main class
     * @return this builder
     */
    public JpackageCLIBuilder mainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "Main class cannot be null");
        this.arguments.add("--main-class " + mainClass);
        return this;
    }

    /**
     * Sets the main JAR file to launch.
     *
     * @param jarPath path to the main JAR
     * @return this builder
     */
    public JpackageCLIBuilder mainJar(String jarPath) {
        Objects.requireNonNull(jarPath, "Main JAR path cannot be null");
        this.arguments.add("--main-jar " + jarPath);
        return this;
    }

    /**
     * Declares the primary module to launch.
     *
     * @param module module name
     * @return this builder
     */
    public JpackageCLIBuilder module(String module) {
        Objects.requireNonNull(module, "Module cannot be null");
        this.arguments.add("--module " + module);
        return this;
    }

    /**
     * Requests a Windows console application.
     *
     * @return this builder
     */
    public JpackageCLIBuilder windowsConsole() {
        this.arguments.add("--win-console");
        return this;
    }

    /**
     * Sets the macOS bundle identifier.
     *
     * @param identifier bundle identifier
     * @return this builder
     */
    public JpackageCLIBuilder macPackageIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "Identifier cannot be null");
        this.arguments.add("--mac-package-identifier " + identifier);
        return this;
    }

    /**
     * Sets the macOS package name.
     *
     * @param name mac package name
     * @return this builder
     */
    public JpackageCLIBuilder macPackageName(String name) {
        Objects.requireNonNull(name, "Mac package name cannot be null");
        this.arguments.add("--mac-package-name " + name);
        return this;
    }

    /**
     * Adds the signing prefix for macOS packages.
     *
     * @param prefix signing prefix
     * @return this builder
     */
    public JpackageCLIBuilder macPackageSigningPrefix(String prefix) {
        Objects.requireNonNull(prefix, "Signing prefix cannot be null");
        this.arguments.add("--mac-package-signing-prefix " + prefix);
        return this;
    }

    /**
     * Enables macOS signing for the package.
     *
     * @return this builder
     */
    public JpackageCLIBuilder macSign() {
        this.arguments.add("--mac-sign");
        return this;
    }

    /**
     * Provides the macOS keychain used for signing.
     *
     * @param keychain keychain name
     * @return this builder
     */
    public JpackageCLIBuilder macSigningKeychain(String keychain) {
        Objects.requireNonNull(keychain, "Keychain cannot be null");
        this.arguments.add("--mac-signing-keychain " + keychain);
        return this;
    }

    /**
     * Sets the username for the macOS signing key.
     *
     * @param userName mac signing user name
     * @return this builder
     */
    public JpackageCLIBuilder macSigningKeyUser(String userName) {
        Objects.requireNonNull(userName, "User name cannot be null");
        this.arguments.add("--mac-signing-key-user-name " + userName);
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
            ProcessExecution.enforceTimeout(process, timeoutDuration, timeoutUnit, "jpackage");

            return process;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to start jpackage process", exception);
        }
    }
}
