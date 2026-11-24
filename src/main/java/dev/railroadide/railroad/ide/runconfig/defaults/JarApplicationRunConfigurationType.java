package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.JarApplicationRunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.session.RunSession;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.icon.RailroadIcon;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Launches executable JARs with configured Java runtime options and tracks their processes.
 */
public class JarApplicationRunConfigurationType extends RunConfigurationType<JarApplicationRunConfigurationData> {
    private final Map<RunConfiguration<?>, Running> runningProcesses = new ConcurrentHashMap<>();

    /**
     * Creates the JAR application type with its localized label and JAR icon.
     */
    public JarApplicationRunConfigurationType() {
        super("railroad.runconfig.jar_application", RailroadIcon.JAR_FILE, Color.web("#e67e22"));
    }

    @Override
    public CompletableFuture<Void> run(
        Project project,
        RunConfiguration<JarApplicationRunConfigurationData> configuration
    ) {
        RunSession session = project.getRunSessions().start(configuration, false);
        var running = new Running(session);
        runningProcesses.put(configuration, running);
        return CompletableFuture.supplyAsync(() -> {
            JarApplicationRunConfigurationData data = configuration.data();
            JDK jre = requireJre(data);
            Path jarPath = requireJarPath(data);
            Path workingDirectory = resolveWorkingDirectory(project, data);
            String[] vmOptions = data.getVmOptions() == null ? new String[0] : data.getVmOptions();
            String[] programArguments = data.getProgramArguments() == null ? new String[0] : data.getProgramArguments();
            Map<String, String> environmentVariables = data.getEnvironmentVariables() == null
                ? Map.of()
                : data.getEnvironmentVariables();

            try {
                String[] command = buildCommand(jre, jarPath, vmOptions, programArguments);
                var builder = new ProcessBuilder()
                    .command(command)
                    .directory(workingDirectory.toFile());
                if (!environmentVariables.isEmpty()) {
                    builder.environment().putAll(environmentVariables);
                }
                Process process;
                synchronized (running) {
                    if (session.isStopRequested())
                        return null;
                    session.appendSystemLine(String.join(" ", command));
                    process = builder.start();
                    running.process = process;
                }
                CompletableFuture<Void> drained = session.connect(process, RunSession.processCharset());
                int exitCode = process.waitFor();
                drained.join();
                return exitCode;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to start Jar Application process", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Jar Application process was interrupted", exception);
            }
        }).handle((exitCode, failure) -> {
            runningProcesses.remove(configuration, running);
            session.finish(exitCode, failure);
            if (failure != null)
                throw failure instanceof CompletionException completion ? completion : new CompletionException(failure);
            if (exitCode != null && exitCode != 0 && !session.isStopRequested())
                throw new IllegalStateException("Jar Application process exited with code: " + exitCode);

            return null;
        });
    }

    private static String[] buildCommand(JDK jre, Path jarPath, String[] vmOptions, String[] programArguments) {
        String javaExecutable = jre.path().resolve("bin").resolve(JDKManager.JAVA_EXECUTABLE_NAME).toString();
        String jarOption = "-jar";
        String jarFile = jarPath.toString();

        String[] command = new String[3 + vmOptions.length + programArguments.length];
        command[0] = javaExecutable;
        System.arraycopy(vmOptions, 0, command, 1, vmOptions.length);
        command[1 + vmOptions.length] = jarOption;
        command[2 + vmOptions.length] = jarFile;
        System.arraycopy(programArguments, 0, command, 3 + vmOptions.length, programArguments.length);

        return command;
    }

    @Override
    public CompletableFuture<Void> debug(
        Project project,
        RunConfiguration<JarApplicationRunConfigurationData> configuration
    ) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Debugging is not supported for Jar Application run configurations."));
    }

    @Override
    public CompletableFuture<Void> stop(
        Project project,
        RunConfiguration<JarApplicationRunConfigurationData> configuration
    ) {
        Running running = runningProcesses.get(configuration);
        if (running != null) {
            running.stop();
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isRunning(Project project, RunConfiguration<JarApplicationRunConfigurationData> configuration) {
        return runningProcesses.containsKey(configuration);
    }

    @Override
    public JarApplicationRunConfigurationData createDataInstance(@UnknownNullability Project project) {
        var data = new JarApplicationRunConfigurationData();
        data.setName("New Jar Application");
        data.setWorkingDirectory(project.getPath());
        data.setJarPath(project.getPath().resolve("app.jar"));
        data.setJre(/* project.getJDKManager().getDefaultJDK() */ JDKManager.getDefaultJDK()); // TODO
        return data;
    }

    @Override
    public Class<JarApplicationRunConfigurationData> getDataClass() {
        return JarApplicationRunConfigurationData.class;
    }

    private static JDK requireJre(JarApplicationRunConfigurationData data) {
        JDK jre = data.getJre();
        if (jre == null)
            throw new IllegalStateException("JRE must be specified for Jar Application run configurations.");

        return jre;
    }

    private static Path requireJarPath(JarApplicationRunConfigurationData data) {
        Path jarPath = data.getJarPath();
        if (jarPath == null)
            throw new IllegalStateException("Jar path must be specified.");

        if (!Files.exists(jarPath))
            throw new IllegalStateException("Jar file does not exist: " + jarPath);

        return jarPath;
    }

    private static Path resolveWorkingDirectory(Project project, JarApplicationRunConfigurationData data) {
        Path workingDirectory = data.getWorkingDirectory();
        if (workingDirectory == null) {
            workingDirectory = project.getPath();
        }

        if (Files.notExists(workingDirectory) || !Files.isDirectory(workingDirectory))
            throw new IllegalStateException(
                "Working directory does not exist or is not a directory: " + workingDirectory);

        return workingDirectory;
    }

    private static final class Running {
        private final RunSession session;
        private Process process;

        private Running(RunSession session) {
            this.session = session;
        }

        private synchronized void stop() {
            session.requestStop();
            if (process != null) {
                process.destroy();
            }
        }
    }
}
