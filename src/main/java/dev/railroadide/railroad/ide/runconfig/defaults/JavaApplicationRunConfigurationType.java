package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.JavaApplicationRunConfigurationData;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.project.Project;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class JavaApplicationRunConfigurationType extends RunConfigurationType<JavaApplicationRunConfigurationData> {
    private final Map<RunConfiguration<?>, Process> runningProcesses = new ConcurrentHashMap<>();

    public JavaApplicationRunConfigurationType() {
        super("railroad.runconfig.java_application", FontAwesomeSolid.BOX, Color.web("#f89820"));
    }

    @Override
    public CompletableFuture<Void> run(Project project, RunConfiguration<JavaApplicationRunConfigurationData> configuration) {
        return execute(project, configuration, false).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Failed to start run session for configuration: {}", configuration.data().getName(), throwable);
            }
        });
    }

    @Override
    public CompletableFuture<Void> debug(Project project, RunConfiguration<JavaApplicationRunConfigurationData> configuration) {
        return execute(project, configuration, true).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Failed to start debug session for configuration: {}", configuration.data().getName(), throwable);
            }
        });
    }

    @Override
    public CompletableFuture<Void> stop(Project project, RunConfiguration<JavaApplicationRunConfigurationData> configuration) {
        Process process = runningProcesses.get(configuration);
        if (process != null && process.isAlive()) {
            process.destroy();
            process.onExit().thenRun(() -> runningProcesses.remove(configuration));
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public JavaApplicationRunConfigurationData createDataInstance(Project project) {
        var data = new JavaApplicationRunConfigurationData();
        data.setName("New Java Application");
        data.setJdk(/*project.getJDKManager().getDefaultJDK()*/ JDKManager.getDefaultJDK()); // TODO
        data.setWorkingDirectory(project.getPath());
        return data;
    }

    @Override
    public Class<JavaApplicationRunConfigurationData> getDataClass() {
        return JavaApplicationRunConfigurationData.class;
    }

    private CompletableFuture<Void> execute(Project project, RunConfiguration<JavaApplicationRunConfigurationData> configuration, boolean debug) {
        JavaApplicationRunConfigurationData data = configuration.data();
        final JDK jdk = data.getJdk();
        final String mainClass = data.getMainClass();
        final Path workingDirectory = data.getWorkingDirectory();
        final boolean buildBeforeRun = data.isBuildBeforeRun();
        final String[] classpathEntries = data.getClasspathEntries();
        final String[] programArguments = data.getProgramArguments();
        final String[] vmOptions = data.getVmOptions();
        final Map<String, String> environmentVariables = data.getEnvironmentVariables() == null
            ? Map.of()
            : data.getEnvironmentVariables();

        if (jdk == null)
            return CompletableFuture.failedFuture(new IllegalStateException("JDK is not specified"));

        if (mainClass == null || mainClass.isBlank())
            return CompletableFuture.failedFuture(new IllegalStateException("Main class is not specified"));

        if (workingDirectory == null)
            return CompletableFuture.failedFuture(new IllegalStateException("Working directory is not specified"));

        if (Files.notExists(workingDirectory) || !Files.isDirectory(workingDirectory))
            return CompletableFuture.failedFuture(new IllegalStateException("Working directory does not exist or is not a directory: " + workingDirectory));

        CompletableFuture<Void> buildFuture = CompletableFuture.completedFuture(null);
        if (buildBeforeRun) {
            buildFuture = project.build(jdk).thenCompose(closeGradleConnection -> {
                closeGradleConnection.run();
                return CompletableFuture.completedFuture((Void) null);
            }).exceptionally(throwable -> {
                System.err.println("Build failed: " + throwable.getMessage());
                throw new IllegalStateException("Build failed before running application", throwable);
            });
        }

        return buildFuture.thenCompose(ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                final int debugPort = debug ? findFreePort() : -1;
                String[] command = buildCommand(jdk, mainClass, classpathEntries, programArguments, vmOptions, debug, debugPort);
                ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE);
                if (!environmentVariables.isEmpty()) {
                    builder.environment().putAll(environmentVariables);
                }

                Process process = builder.start();
                runningProcesses.put(configuration, process);

                // Start consuming output (must be done asynchronously)
                new ProcessOutputHandler(process, configuration.data().getName()).run();

                if (debug && debugPort > 0) {
                    // TODO: trigger IDE debugger attachment here
                    Railroad.LOGGER.debug("DEBUG: IDE should attach debugger to port {} for configuration: {}", debugPort, configuration.data().getName());
                }

                process.onExit().thenAccept(p -> {
                    runningProcesses.remove(configuration);
                    if (p.exitValue() != 0) {
                        Railroad.LOGGER.error("Application process exited with code: {}", p.exitValue());
                    } else {
                        Railroad.LOGGER.debug("Application process finished successfully.");
                    }
                });

                return null;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to start Jar Application process", exception);
            }
        }));
    }

    private static String[] buildCommand(JDK jdk, String mainClass,
                                         String[] classpathEntries, String[] programArguments,
                                         String[] vmOptions, boolean debug, int debugPort) {
        String javaExecutable = jdk.path().resolve("bin").resolve(JDKManager.JAVA_EXECUTABLE_NAME).toString();
        String[] vm = vmOptions == null ? new String[0] : vmOptions;
        String[] args = programArguments == null ? new String[0] : programArguments;
        String classpath = String.join(File.pathSeparator, classpathEntries != null ? classpathEntries : new String[0]);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable);

        if (debug) {
            if (debugPort <= 0) {
                throw new IllegalStateException("Debug port must be provided when debug mode is enabled.");
            }
            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=%d".formatted(debugPort));
        }

        command.addAll(List.of(vm));
        if (classpathEntries != null && classpathEntries.length > 0) {
            command.add("-cp");
            command.add(classpath);
        }

        command.add(mainClass);
        command.addAll(List.of(args));
        return command.toArray(new String[0]);
    }

    private static int findFreePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to find a free port for debugging", exception);
        }
    }

    private record ProcessOutputHandler(Process process, String name) implements Runnable {
        @Override
        public void run() {
            // In the future IDE, we will read from process.getInputStream() and process.getErrorStream()
            // and display it in the IDE console.
            // For now, we just print to System.out/err.
            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[" + name + " OUT] " + line);
                    }
                } catch (IOException exception) {
                    System.err.println("[" + name + " ERR] Error reading stdout: " + exception.getMessage());
                }
            }).start();

            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("[" + name + " ERR] " + line);
                    }
                } catch (IOException exception) {
                    System.err.println("[" + name + " ERR] Error reading stderr: " + exception.getMessage());
                }
            }).start();
        }
    }
}
