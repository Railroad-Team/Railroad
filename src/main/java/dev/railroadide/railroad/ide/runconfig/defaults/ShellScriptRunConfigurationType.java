package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.ShellScriptRunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.defaults.data.ShellScriptRunConfigurationData.ExecuteMode;
import dev.railroadide.railroad.project.Project;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ShellScriptRunConfigurationType extends RunConfigurationType<ShellScriptRunConfigurationData> {
    private final Map<RunConfiguration<?>, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<RunConfiguration<?>, Path> temporaryScripts = new ConcurrentHashMap<>();

    public ShellScriptRunConfigurationType() {
        super("railroad.runconfig.shellscript", FontAwesomeSolid.TERMINAL, Color.web("#919191"));
    }

    @Override
    public CompletableFuture<Void> run(Project project, RunConfiguration<ShellScriptRunConfigurationData> configuration) {
        return execute(project, configuration).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Failed to start shell script for configuration: {}", configuration.data().getName(), throwable);
            }
        });
    }

    @Override
    public CompletableFuture<Void> debug(Project project, RunConfiguration<ShellScriptRunConfigurationData> configuration) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Debugging shell script run configurations is not supported."));
    }

    @Override
    public CompletableFuture<Void> stop(Project project, RunConfiguration<ShellScriptRunConfigurationData> configuration) {
        Process process = runningProcesses.get(configuration);
        if (process != null && process.isAlive()) {
            process.destroy();
            process.onExit().thenRun(() -> {
                runningProcesses.remove(configuration);
                cleanupTemporaryScript(configuration);
            });
        } else {
            cleanupTemporaryScript(configuration);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public ShellScriptRunConfigurationData createDataInstance(Project project) {
        var data = new ShellScriptRunConfigurationData();
        data.setName("New Shell Script");
        data.setWorkingDirectory(project.getPath());
        data.setScriptPath(project.getPath().resolve("script.sh"));
        return data;
    }

    @Override
    public Class<ShellScriptRunConfigurationData> getDataClass() {
        return ShellScriptRunConfigurationData.class;
    }

    private CompletableFuture<Void> execute(Project project, RunConfiguration<ShellScriptRunConfigurationData> configuration) {
        return CompletableFuture.runAsync(() -> {
            ShellScriptRunConfigurationData data = configuration.data();
            Path workingDirectory = resolveWorkingDirectory(project, data);
            ScriptDescriptor scriptDescriptor = resolveScriptDescriptor(data, workingDirectory);
            List<String> command = buildCommand(data, scriptDescriptor.scriptPath());
            Map<String, String> environment = data.getEnvironmentVariables();

            if (scriptDescriptor.isTemporary()) {
                temporaryScripts.put(configuration, scriptDescriptor.scriptPath());
            }

            try {
                ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
                if (data.isExecuteInTerminal()) {
                    builder.inheritIO();
                } else {
                    builder.redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE);
                }

                if (!environment.isEmpty()) {
                    builder.environment().putAll(environment);
                }

                Process process = builder.start();
                runningProcesses.put(configuration, process);

                if (!data.isExecuteInTerminal()) {
                    new ProcessOutputHandler(process, configuration.data().getName()).run();
                }

                process.onExit().thenAccept(p -> {
                    runningProcesses.remove(configuration);
                    cleanupTemporaryScript(configuration);
                    if (p.exitValue() != 0) {
                        Railroad.LOGGER.error("Shell script exited with code {} for configuration: {}", p.exitValue(),
                            configuration.data().getName());
                    } else {
                        Railroad.LOGGER.debug("Shell script finished successfully for configuration: {}", configuration.data().getName());
                    }
                });
            } catch (IOException exception) {
                cleanupTemporaryScript(configuration);
                throw new IllegalStateException("Failed to start shell script process", exception);
            }
        });
    }

    private static Path resolveWorkingDirectory(Project project, ShellScriptRunConfigurationData data) {
        Path workingDirectory = data.getWorkingDirectory();
        if (workingDirectory == null) {
            workingDirectory = project.getPath();
        }

        if (Files.notExists(workingDirectory) || !Files.isDirectory(workingDirectory))
            throw new IllegalStateException("Working directory does not exist or is not a directory: " + workingDirectory);

        return workingDirectory;
    }

    private static ScriptDescriptor resolveScriptDescriptor(ShellScriptRunConfigurationData data, Path workingDirectory) {
        ExecuteMode mode = data.getExecuteMode() == null ? ExecuteMode.FILE : data.getExecuteMode();
        return switch (mode) {
            case FILE -> new ScriptDescriptor(resolveScriptPath(data.getScriptPath(), workingDirectory), false);
            case TEXT -> new ScriptDescriptor(createScriptFromText(data.getScriptText()), true);
        };
    }

    private static Path resolveScriptPath(Path scriptPath, Path workingDirectory) {
        if (scriptPath == null)
            throw new IllegalStateException("Script path must be specified when executing a file.");

        Path resolved = scriptPath.isAbsolute() ? scriptPath : workingDirectory.resolve(scriptPath).normalize();
        if (Files.notExists(resolved))
            throw new IllegalStateException("Script file does not exist: " + resolved);

        return resolved;
    }

    private static Path createScriptFromText(String scriptText) {
        if (scriptText == null || scriptText.isBlank())
            throw new IllegalStateException("Script text must be provided when executing inline content.");

        try {
            Path tempFile = Files.createTempFile("railroad-script-", ".tmp");
            Files.writeString(tempFile, scriptText, StandardCharsets.UTF_8);
            tempFile.toFile().deleteOnExit();
            return tempFile;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary script file", exception);
        }
    }

    private static List<String> buildCommand(ShellScriptRunConfigurationData data, Path scriptPath) {
        String interpreterPath = Objects.requireNonNull(data.getInterpreterPath(), "interpreterPath");
        if (interpreterPath.isBlank())
            throw new IllegalStateException("Interpreter path is not specified");

        String[] interpreterArgs = data.getInterpreterArgs() == null ? new String[0] : data.getInterpreterArgs();
        String[] scriptArgs = data.getScriptArgs() == null ? new String[0] : data.getScriptArgs();

        List<String> command = new ArrayList<>();
        command.add(interpreterPath);
        command.addAll(List.of(interpreterArgs));
        command.add(scriptPath.toAbsolutePath().toString());
        command.addAll(List.of(scriptArgs));
        return command;
    }

    private void cleanupTemporaryScript(RunConfiguration<?> configuration) {
        Path script = temporaryScripts.remove(configuration);
        if (script != null) {
            try {
                Files.deleteIfExists(script);
            } catch (IOException exception) {
                Railroad.LOGGER.warn("Failed to delete temporary script file: {}", script, exception);
            }
        }
    }

    private record ScriptDescriptor(Path scriptPath, boolean isTemporary) {
    }

    private record ProcessOutputHandler(Process process, String name) implements Runnable {
        @Override
        public void run() {
            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[" + name + " OUT] " + line);
                    }
                } catch (IOException exception) {
                    System.err.println("[" + name + " ERR] Error reading stdout: " + exception.getMessage());
                }
            }, name + "-stdout-reader").start();

            new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("[" + name + " ERR] " + line);
                    }
                } catch (IOException exception) {
                    System.err.println("[" + name + " ERR] Error reading stderr: " + exception.getMessage());
                }
            }, name + "-stderr-reader").start();
        }
    }
}
