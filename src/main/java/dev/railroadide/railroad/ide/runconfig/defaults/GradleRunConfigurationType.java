package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.debug.jdi.JdiDebugSession;
import dev.railroadide.railroad.debug.model.DebugEndpoint;
import dev.railroadide.railroad.debug.source.DebugSource;
import dev.railroadide.railroad.debug.source.LibrarySourceIndex;
import dev.railroadide.railroad.debug.source.ProjectSourceResolver;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.ide.runconfig.session.RunSession;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import dev.railroadide.railroad.utility.icon.RailroadBrandsIcon;
import javafx.scene.paint.Color;
import org.gradle.tooling.*;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Executes Gradle tasks through the Tooling API and tracks cancellable build connections.
 */
public class GradleRunConfigurationType extends RunConfigurationType<GradleRunConfigurationData> {
    private final Supplier<GradleConnector> connectors;
    private final Map<UUID, GradleExecutionHandle> executions = new ConcurrentHashMap<>();
    private final ExecutorService handleCloser = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "gradle-handle-closer");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Creates the Gradle run type with its localized label and Gradle icon.
     */
    public GradleRunConfigurationType() {
        this(() -> GradleConnector.newConnector().useBuildDistribution());
    }

    /**
     * Creates an executor with a connector factory, allowing embedded Gradle installations.
     *
     * @param connectors factory producing a fresh connector for each execution
     */
    protected GradleRunConfigurationType(Supplier<GradleConnector> connectors) {
        super("railroad.runconfig.gradle", RailroadBrandsIcon.GRADLE, Color.web("#6dc24f"));
        this.connectors = connectors;
    }

    @Override
    public CompletableFuture<Void> run(Project project, RunConfiguration<GradleRunConfigurationData> configuration) {
        return execute(project, configuration, false);
    }

    @Override
    public CompletableFuture<Void> debug(Project project, RunConfiguration<GradleRunConfigurationData> configuration) {
        return execute(project, configuration, true);
    }

    private CompletableFuture<Void> execute(
        Project project,
        RunConfiguration<GradleRunConfigurationData> configuration,
        boolean debug
    ) {
        var execution = new GradleExecutionHandle();
        if (executions.putIfAbsent(configuration.uuid(), execution) != null)
            return CompletableFuture
                .failedFuture(new IllegalStateException("This Gradle configuration is already running"));
        execution.session = project.getRunSessions().start(configuration, debug);
        CompletableFuture.runAsync(() -> {
            try {
                executeGradleBuild(project, configuration, execution, debug);
            } catch (Throwable failure) {
                finish(configuration, execution, failure);
            } finally {
                execution.setupComplete.complete(null);
            }
        });
        return execution.completion;
    }

    @Override
    public CompletableFuture<Void> stop(Project project, RunConfiguration<GradleRunConfigurationData> configuration) {
        GradleExecutionHandle execution = executions.get(configuration.uuid());
        if (execution == null)
            return CompletableFuture.completedFuture(null);
        execution.session.requestStop();
        execution.cancel();
        finish(configuration, execution, new CancellationException("Gradle execution stopped"));
        return execution.cleaned;
    }

    @Override
    public boolean isRunning(Project project, RunConfiguration<GradleRunConfigurationData> configuration) {
        return executions.containsKey(configuration.uuid());
    }

    @Override
    public boolean isDebuggingSupported(Project project, RunConfiguration<GradleRunConfigurationData> configuration) {
        return true;
    }

    @Override
    public GradleRunConfigurationData createDataInstance(@UnknownNullability Project project) {
        var data = new GradleRunConfigurationData();
        data.setName("New Gradle Configuration");
        data.setGradleProjectPath(project.getPath());
        data.setJavaHome(/* project.getJDKManager().getDefaultJDK() */ JDKManager.getDefaultJDK()); // TODO
        return data;
    }

    @Override
    public Class<GradleRunConfigurationData> getDataClass() {
        return GradleRunConfigurationData.class;
    }

    private static final class GradleExecutionHandle {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final CompletableFuture<Void> cleaned = new CompletableFuture<>();
        private final CompletableFuture<Void> setupComplete = new CompletableFuture<>();
        private volatile boolean ended;
        private boolean finishing;
        private volatile RunSession session;
        private OutputStream stdout;
        private OutputStream stderr;
        private ProjectConnection connection;
        private volatile CancellationTokenSource cancellation;
        private Path script;
        private CompletableFuture<JdiDebugSession> attachment = CompletableFuture.completedFuture(null);

        private void cancel() {
            ended = true;
            CancellationTokenSource token = cancellation;
            if (token != null) {
                token.cancel();
            }
        }
    }

    private static String requireTask(GradleRunConfigurationData data) {
        String task = data.getTask();
        if (task == null || task.isBlank())
            throw new IllegalStateException("Gradle task must be specified.");

        return task;
    }

    private static Path requireGradleProjectPath(GradleRunConfigurationData data) {
        Path path = data.getGradleProjectPath();
        if (path == null)
            throw new IllegalStateException("Gradle project path must be specified.");

        if (Files.notExists(path) || !Files.isDirectory(path))
            throw new IllegalStateException("Gradle project path does not exist or is not a directory: " + path);

        return path;
    }

    private static JDK requireJavaHome(GradleRunConfigurationData data) {
        JDK javaHome = data.getJavaHome();
        if (javaHome == null)
            throw new IllegalStateException("Java home must be specified for Gradle run configurations.");

        return javaHome;
    }

    private void finish(
        RunConfiguration<GradleRunConfigurationData> configuration,
        GradleExecutionHandle execution,
        Throwable failure
    ) {
        synchronized (execution) {
            if (execution.finishing)
                return;
            execution.finishing = true;
            execution.ended = true;
        }
        handleCloser.execute(() -> {
            Throwable outcome = failure;
            try {
                execution.setupComplete.join();
                if (failure != null && execution.cancellation != null) {
                    execution.cancellation.cancel();
                }
                JdiDebugSession session = execution.attachment.handle((attached, _) -> attached).join();
                if (session != null) {
                    if (failure == null) {
                        session.detach().join();
                    } else {
                        session.terminate().join();
                    }
                }
            } catch (Throwable cleanupFailure) {
                if (outcome == null) {
                    outcome = cleanupFailure;
                }
            } finally {
                try {
                    if (execution.connection != null) {
                        execution.connection.close();
                    }
                } catch (Throwable cleanupFailure) {
                    if (outcome == null) {
                        outcome = cleanupFailure;
                    }
                } finally {
                    try {
                        if (execution.script != null) {
                            Files.deleteIfExists(execution.script);
                        }
                    } catch (IOException cleanupFailure) {
                        Railroad.LOGGER.error("Failed to delete Gradle debug init script", cleanupFailure);
                        if (outcome == null) {
                            outcome = cleanupFailure;
                        }
                    }
                }
            }
            closeQuietly(execution.stdout);
            closeQuietly(execution.stderr);
            execution.session.finish(null, outcome);
            executions.remove(configuration.uuid(), execution);
            if (outcome == null) {
                execution.completion.complete(null);
            } else {
                execution.completion.completeExceptionally(outcome);
            }
            execution.cleaned.complete(null);
        });
    }

    private void executeGradleBuild(
        Project project,
        RunConfiguration<GradleRunConfigurationData> configuration,
        GradleExecutionHandle execution,
        boolean debug
    ) throws IOException {
        GradleRunConfigurationData data = configuration.data();
        boolean editorMain = data.getMainSource() != null;
        String task = editorMain
            ? ":railroadMain" + configuration.uuid().toString().replace("-", "")
            : requireTask(data).trim();
        if (debug && !task.startsWith(":")) {
            task = ":" + task;
        }
        Path gradleProjectPath = requireGradleProjectPath(data);
        Map<String, String> environmentVariables = new HashMap<>(System.getenv());
        if (data.getEnvironmentVariables() != null) {
            environmentVariables.putAll(data.getEnvironmentVariables());
        }
        String[] vmOptions = data.getVmOptions() == null ? new String[0] : data.getVmOptions();
        JDK javaHome = editorMain ? data.getJavaHome() : requireJavaHome(data);

        if (execution.ended)
            return;
        execution.cancellation = GradleConnector.newCancellationTokenSource();
        if (execution.ended)
            return;
        int port = -1;
        if (debug) {
            try (var socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
        }
        if (editorMain || debug) {
            execution.script = Files.createTempFile("railroad-launch-", ".init.gradle");
            String init = editorMain ? loadRunMainClassInitScript() : "";
            Files.writeString(execution.script, init + (debug ? debugInitScript(task, port) : ""));
        }
        if (execution.ended)
            return;
        execution.connection = connectors.get()
            .forProjectDirectory(gradleProjectPath.toFile()).connect();
        if (execution.ended)
            return;
        RunSession runSession = execution.session;
        Charset charset = Charset.defaultCharset();
        execution.stdout = runSession.stdout(charset);
        execution.stderr = runSession.stderr(charset);
        runSession.appendSystemLine("gradle " + task);
        // Colour output switches Gradle to its rich console, whose progress bar redraws lines with cursor movement.
        BuildLauncher launcher = execution.connection.newBuild()
            .forTasks(task)
            .setJvmArguments(vmOptions)
            .setEnvironmentVariables(environmentVariables)
            .setColorOutput(false)
            .withCancellationToken(execution.cancellation.token())
            .setStandardOutput(execution.stdout)
            .setStandardError(execution.stderr)
            .setStandardInput(runSession.input(charset));
        if (javaHome != null) {
            launcher.setJavaHome(javaHome.path().toFile());
        }
        if (execution.script != null) {
            var arguments = new ArrayList<>(
                List.of("--init-script", execution.script.toString(), "--no-configuration-cache"));
            if (editorMain) {
                arguments.add("--no-configure-on-demand");
                arguments.add("-PrailroadMainSource=" + data.getMainSource());
                arguments.add("-PrailroadMainClass=" + data.getMainClass());
                arguments.add("-PrailroadMainTask=" + task.substring(1));
                if (project.getGradleManager().getGradleSettings().isOfflineMode()) {
                    arguments.add("--offline");
                }
            }
            launcher.withArguments(arguments);
        }
        if (execution.ended)
            return;
        launcher.run(new ResultHandler<>() {
            @Override
            public void onComplete(Void result) {
                finish(configuration, execution, null);
            }

            @Override
            public void onFailure(GradleConnectionException failure) {
                finish(configuration, execution, failure);
            }
        });
        if (debug && !execution.ended) {
            Path root = ProjectPathIdentityUtils.normalize(project.getPath());
            int moduleSeparator = task.lastIndexOf(':');
            String modulePath = !editorMain && moduleSeparator > 0 ? task.substring(0, moduleSeparator) : null;
            var dependencies = LibrarySourceIndex.forProject(project, List.of(), modulePath,
                editorMain || modulePath != null ? null : gradleProjectPath);
            var resolver = editorMain
                ? ProjectSourceResolver.create(root, gradleProjectPath.resolve(data.getMainSource()),
                    data.getMainClass(), dependencies)
                : ProjectSourceResolver.create(root, dependencies);
            if (execution.ended)
                return;
            execution.attachment = project.getDebuggingManager().startSession(
                new DebugEndpoint("127.0.0.1", port), resolver,
                Services.BREAKPOINT_SERVICE,
                breakpoint -> breakpoint.source() instanceof DebugSource.FileSource(Path file)
                    && (ProjectPathIdentityUtils.normalize(file).startsWith(root)
                        || resolver.isDependencySource(breakpoint.source())),
                event -> Railroad.LOGGER.debug("Gradle debugger event: {}", event),
                Duration.ofMinutes(editorMain ? 10 : 1), () -> execution.ended);
            execution.attachment.whenComplete((session, failure) -> {
                if (failure != null && !execution.ended) {
                    finish(configuration, execution, failure);
                }
            });
        }
    }

    private static String loadRunMainClassInitScript() throws IOException {
        try (var input = GradleRunConfigurationType.class
            .getResourceAsStream("/assets/railroad/scripts/init-run-main-class.gradle")) {
            if (input == null)
                throw new IOException("Missing init-run-main-class.gradle");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8) + "\n";
        }
    }

    private static void closeQuietly(OutputStream stream) {
        if (stream == null)
            return;

        try {
            stream.close();
        } catch (IOException exception) {
            Railroad.LOGGER.debug("Failed to flush Gradle run output", exception);
        }
    }

    /**
     * Configures only the selected task's forked JVM without editing the user's build.
     *
     * @param task absolute Gradle task path
     * @param port loopback JDWP listening port
     * @return Groovy initialization script
     */
    public static String debugInitScript(String task, int port) {
        String escaped = task.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\r", "\\r").replace("\n", "\\n");
        return """
            if (gradle.parent != null) return
            def railroadTaskPath = '%s'
            gradle.projectsEvaluated {
                def target = gradle.rootProject.tasks.findByPath(railroadTaskPath)
                if (target == null) {
                    throw new GradleException("Railroad could not find Gradle task " + railroadTaskPath)
                }
                if (!(target instanceof org.gradle.api.tasks.JavaExec) &&
                    !(target instanceof org.gradle.api.tasks.testing.Test)) {
                    throw new GradleException("Task '" + railroadTaskPath +
                        "' does not launch a debuggable JVM. Only JavaExec and Test tasks are currently supported.")
                }
                target.debugOptions {
                    enabled = true
                    host = '127.0.0.1'
                    port = %d
                    server = true
                    suspend = true
                }
                if (target instanceof org.gradle.api.tasks.testing.Test) {
                    target.maxParallelForks = 1
                    target.forkEvery = 0
                    target.outputs.upToDateWhen { false }
                    target.outputs.doNotCacheIf('Railroad debugging') { true }
                }
            }
            """.formatted(escaped, port);
    }
}
