package dev.railroadide.railroad;

import dev.railroadide.railroad.utility.OperatingSystem;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Launches a new process of the Railroad IDE application.
 */
public final class RailroadProcessLauncher {
    private RailroadProcessLauncher() {
    }

    /**
     * Launches a new process of the Railroad IDE application with the given project path.
     *
     * @param projectPath The path to the project to open in the new process.
     * @throws IOException If an I/O error occurs while launching the new process.
     */
    public static void openProject(Path projectPath) throws IOException {
        String projectArgument = "--project=" + projectPath.toAbsolutePath().normalize();

        // Packaged jpackage application
        String packagedLauncher = System.getProperty("jpackage.app-path");
        if (packagedLauncher != null && !packagedLauncher.isBlank()) {
            new ProcessBuilder(packagedLauncher, projectArgument).start();
            return;
        }

        launchDevelopmentProcess(projectArgument);
    }

    private static void launchDevelopmentProcess(String projectArgument) throws IOException {
        Path javaExecutable = Path.of(
            System.getProperty("java.home"),
            "bin",
            OperatingSystem.isWindows() ? "java.exe" : "java");

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());

        // Preserve JVM options supplied by Gradle/IntelliJ.
        for (String argument : ManagementFactory
            .getRuntimeMXBean()
            .getInputArguments()) {

            // A second process cannot reuse the debugger's listening port.
            if (argument.startsWith("-agentlib:jdwp=")
                || argument.startsWith("-Xrunjdwp:"))
                continue;

            command.add(argument);
        }

        addModulePathIfNecessary(command);

        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(RailroadLauncher.class.getName());
        command.add(projectArgument);

        new ProcessBuilder(command)
            .directory(Path.of(System.getProperty("user.dir")).toFile())
            .inheritIO()
            .start();
    }

    private static void addModulePathIfNecessary(List<String> command) {
        boolean alreadyPresent = command.stream().anyMatch(argument -> argument.equals("--module-path")
            || argument.equals("-p")
            || argument.startsWith("--module-path="));

        if (alreadyPresent)
            return;

        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath == null || modulePath.isBlank())
            return;

        command.add("--module-path");
        command.add(modulePath);

        String modules = ModuleLayer.boot()
            .modules()
            .stream()
            .map(Module::getName)
            .filter(name -> name.startsWith("javafx."))
            .sorted()
            .collect(Collectors.joining(","));

        if (!modules.isBlank()) {
            command.add("--add-modules");
            command.add(modules);
        }
    }
}
