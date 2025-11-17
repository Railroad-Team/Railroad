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

    public JpackageCLIBuilder optionFile(Path optionFile) {
        Objects.requireNonNull(optionFile, "Option file cannot be null");
        this.arguments.add("@" + optionFile);
        return this;
    }

    public JpackageCLIBuilder packageType(String type) {
        Objects.requireNonNull(type, "Package type cannot be null");
        this.arguments.add("--type " + type);
        return this;
    }

    public JpackageCLIBuilder appVersion(String version) {
        Objects.requireNonNull(version, "Version cannot be null");
        this.arguments.add("--app-version " + version);
        return this;
    }

    public JpackageCLIBuilder copyright(String copyright) {
        Objects.requireNonNull(copyright, "Copyright cannot be null");
        this.arguments.add("--copyright " + copyright);
        return this;
    }

    public JpackageCLIBuilder description(String description) {
        Objects.requireNonNull(description, "Description cannot be null");
        this.arguments.add("--description " + description);
        return this;
    }

    public JpackageCLIBuilder help() {
        this.arguments.add("--help");
        return this;
    }

    public JpackageCLIBuilder icon(Path iconPath) {
        Objects.requireNonNull(iconPath, "Icon path cannot be null");
        this.arguments.add("--icon " + iconPath);
        return this;
    }

    public JpackageCLIBuilder applicationName(String name) {
        Objects.requireNonNull(name, "Application name cannot be null");
        this.arguments.add("--name " + name);
        return this;
    }

    public JpackageCLIBuilder destination(Path destination) {
        Objects.requireNonNull(destination, "Destination cannot be null");
        this.arguments.add("--dest " + destination);
        return this;
    }

    public JpackageCLIBuilder resourceDirectory(Path resourceDir) {
        Objects.requireNonNull(resourceDir, "Resource directory cannot be null");
        this.arguments.add("--resource-dir " + resourceDir);
        return this;
    }

    public JpackageCLIBuilder tempDirectory(Path tempDir) {
        Objects.requireNonNull(tempDir, "Temp directory cannot be null");
        this.arguments.add("--temp " + tempDir);
        return this;
    }

    public JpackageCLIBuilder vendor(String vendor) {
        Objects.requireNonNull(vendor, "Vendor cannot be null");
        this.arguments.add("--vendor " + vendor);
        return this;
    }

    public JpackageCLIBuilder verbose() {
        this.arguments.add("--verbose");
        return this;
    }

    public JpackageCLIBuilder versionInfo() {
        this.arguments.add("--version");
        return this;
    }

    public JpackageCLIBuilder addModules(String... modules) {
        Objects.requireNonNull(modules, "Modules cannot be null");
        this.arguments.add("--add-modules " + String.join(",", modules));
        return this;
    }

    public JpackageCLIBuilder modulePath(String... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        this.arguments.add("--module-path " + String.join(File.pathSeparator, modulePaths));
        return this;
    }

    public JpackageCLIBuilder modulePath(Path... modulePaths) {
        Objects.requireNonNull(modulePaths, "Module path entries cannot be null");
        return modulePath(Arrays.stream(modulePaths).map(Path::toString).toArray(String[]::new));
    }

    public JpackageCLIBuilder jlinkOptions(String... options) {
        Objects.requireNonNull(options, "jlink options cannot be null");
        this.arguments.add("--jlink-options " + String.join(" ", options));
        return this;
    }

    public JpackageCLIBuilder runtimeImage(Path runtimeImage) {
        Objects.requireNonNull(runtimeImage, "Runtime image path cannot be null");
        this.arguments.add("--runtime-image " + runtimeImage);
        return this;
    }

    public JpackageCLIBuilder input(Path inputDirectory) {
        Objects.requireNonNull(inputDirectory, "Input directory cannot be null");
        this.arguments.add("--input " + inputDirectory);
        return this;
    }

    public JpackageCLIBuilder appContent(String... contentPaths) {
        Objects.requireNonNull(contentPaths, "App content paths cannot be null");
        this.arguments.add("--app-content " + String.join(",", contentPaths));
        return this;
    }

    public JpackageCLIBuilder addLauncher(String name, Path propertiesFile) {
        Objects.requireNonNull(name, "Launcher name cannot be null");
        Objects.requireNonNull(propertiesFile, "Launcher properties files cannot be null");
        this.arguments.add("--add-launcher " + name + "=" + propertiesFile);
        return this;
    }

    public JpackageCLIBuilder launcherArguments(String arguments) {
        Objects.requireNonNull(arguments, "Launcher arguments cannot be null");
        this.arguments.add("--arguments " + arguments);
        return this;
    }

    public JpackageCLIBuilder launcherJavaOptions(String options) {
        Objects.requireNonNull(options, "Java options cannot be null");
        this.arguments.add("--java-options " + options);
        return this;
    }

    public JpackageCLIBuilder mainClass(String mainClass) {
        Objects.requireNonNull(mainClass, "Main class cannot be null");
        this.arguments.add("--main-class " + mainClass);
        return this;
    }

    public JpackageCLIBuilder mainJar(String jarPath) {
        Objects.requireNonNull(jarPath, "Main JAR path cannot be null");
        this.arguments.add("--main-jar " + jarPath);
        return this;
    }

    public JpackageCLIBuilder module(String module) {
        Objects.requireNonNull(module, "Module cannot be null");
        this.arguments.add("--module " + module);
        return this;
    }

    public JpackageCLIBuilder windowsConsole() {
        this.arguments.add("--win-console");
        return this;
    }

    public JpackageCLIBuilder macPackageIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "Identifier cannot be null");
        this.arguments.add("--mac-package-identifier " + identifier);
        return this;
    }

    public JpackageCLIBuilder macPackageName(String name) {
        Objects.requireNonNull(name, "Mac package name cannot be null");
        this.arguments.add("--mac-package-name " + name);
        return this;
    }

    public JpackageCLIBuilder macPackageSigningPrefix(String prefix) {
        Objects.requireNonNull(prefix, "Signing prefix cannot be null");
        this.arguments.add("--mac-package-signing-prefix " + prefix);
        return this;
    }

    public JpackageCLIBuilder macSign() {
        this.arguments.add("--mac-sign");
        return this;
    }

    public JpackageCLIBuilder macSigningKeychain(String keychain) {
        Objects.requireNonNull(keychain, "Keychain cannot be null");
        this.arguments.add("--mac-signing-keychain " + keychain);
        return this;
    }

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
