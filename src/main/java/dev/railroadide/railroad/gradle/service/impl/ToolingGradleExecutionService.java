package dev.railroadide.railroad.gradle.service.impl;

import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.utility.DecodingOutputStream;
import dev.railroadide.railroad.gradle.service.GradleExecutionService;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionHandle;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionResult;
import dev.railroadide.railroad.gradle.service.task.GradleTaskState;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.DownloadProgress;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.gradle.tooling.*;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.FileDownloadFinishEvent;

import dev.railroadide.railroad.ide.diagnostics.BuildProblems;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.io.OutputStream;

/**
 * Implementation of {@link GradleExecutionService} using the Gradle Tooling API.
 */
public class ToolingGradleExecutionService implements GradleExecutionService {
    private final Project project;
    private final GradleEnvironment environment;
    private final Executor executor;

    private final Map<UUID, GradleTaskExecutionHandle> runningTasks = new ConcurrentHashMap<>();
    private final Deque<GradleTaskExecutionRequest> recentRequests = new ArrayDeque<>();

    /**
     * Creates a new ToolingGradleExecutionService.
     *
     * @param project the project for which Gradle tasks will be executed
     * @param environment the Gradle environment configuration
     * @param executor the executor for running tasks asynchronously
     */
    public ToolingGradleExecutionService(Project project, GradleEnvironment environment, Executor executor) {
        this.project = Objects.requireNonNull(project);
        this.environment = Objects.requireNonNull(environment);
        this.executor = Objects.requireNonNull(executor);
    }

    private static int findFreePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to find a free port for debugging", exception);
        }
    }

    private static String bufferToString(Queue<String> buffer) {
        var stringBuilder = new StringBuilder();
        for (String line : buffer) {
            stringBuilder.append(line).append(System.lineSeparator());
        }

        return stringBuilder.toString();
    }

    @Override
    public GradleTaskExecutionHandle runTask(
        GradleTaskExecutionRequest request,
        BuildProblems.@Nullable Report problems
    ) {
        var handle = new ToolingGradleTaskExecutionHandle(request);
        runningTasks.put(handle.id(), handle);

        var progress = ActivityProgress.current();
        CompletableFuture<GradleTaskExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            handle.updateState(GradleTaskState.STARTING, "railroad.gradle.execution.starting");
            try {
                GradleTaskExecutionResult result = execute(request, handle, progress, problems);
                handle.updateState(GradleTaskState.COMPLETED, "railroad.gradle.execution.completed");
                return result;
            } catch (BuildCancelledException | CancellationException exception) {
                handle.updateState(GradleTaskState.CANCELLED, "railroad.gradle.execution.cancelled");
                throw exception;
            } catch (Exception exception) {
                handle.updateState(GradleTaskState.FAILED, "railroad.gradle.execution.failed");
                if (problems != null) {
                    problems.finish(exception);
                }

                throw new CompletionException(exception);
            } finally {
                if (problems != null) {
                    problems.finish(null);
                }

                runningTasks.remove(handle.id());

                recentRequests.addFirst(request);
                if (recentRequests.size() > 10) {
                    recentRequests.removeLast();
                }
            }
        }, executor);

        handle.setResultFuture(future);
        return handle;
    }

    @Override
    public List<GradleTaskExecutionHandle> getRunningTasks() {
        return List.copyOf(runningTasks.values());
    }

    @Override
    public List<GradleTaskExecutionHandle> stopAllRunningTasks() {
        return runningTasks.values().stream()
            .peek(GradleTaskExecutionHandle::cancel)
            .toList();
    }

    @Override
    public List<GradleTaskExecutionRequest> getRecentRequests() {
        return List.copyOf(recentRequests);
    }

    private GradleTaskExecutionResult execute(
        GradleTaskExecutionRequest request,
        ToolingGradleTaskExecutionHandle handle,
        ActivityProgress progress,
        BuildProblems.@Nullable Report problems
    ) throws IOException {
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(project.getPath().toFile());
        ToolingGradleModelService.configureConnector(connector, environment);

        CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
        handle.attachCancellationToken(tokenSource);

        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher build = connection.newBuild()
                .forTasks(request.taskPath());

            List<String> args = buildArguments(request);
            if (!args.isEmpty()) {
                build.withArguments(args);
            }

            environment.jvm().ifPresent(jvm -> {
                build.setJavaHome(jvm.path().toFile());
                String jvmArgsStr = environment.jvmArgumentsFor(request, jvm);
                if (jvmArgsStr != null && !jvmArgsStr.isBlank()) {
                    List<String> jvmArgs = Arrays.stream(jvmArgsStr.split("\\s+"))
                        .filter(arg -> !arg.isBlank())
                        .toList();
                    build.addJvmArguments(jvmArgs);
                }
            });

            if (!request.environment().isEmpty()) {
                build.setEnvironmentVariables(request.environment());
            }

            build.withCancellationToken(tokenSource.token());

            var charset = Charset.defaultCharset();
            OutputStream stdout = problems == null
                ? new DecodingOutputStream(charset, handle::emitOutput)
                : problems.output(charset, handle::emitOutput);
            OutputStream stderr = problems == null
                ? new DecodingOutputStream(charset, handle::emitError)
                : problems.output(charset, handle::emitError);
            build.setStandardOutput(stdout);
            build.setStandardError(stderr);
            if (problems != null) {
                GradleBuildProblems.listen(build, problems);
            }

            var downloads = new DownloadProgress(progress);
            build.addProgressListener((ProgressListener) event -> {
                String msg = event.getDescriptor() != null
                    ? event.getDescriptor().getName()
                    : event.getDisplayName();
                double fraction = -1;
                if (event instanceof StatusEvent status && status.getTotal() > 0) {
                    fraction = Math.clamp((double) status.getProgress() / status.getTotal(), 0, 1);
                }

                handle.updateProgress(GradleTaskState.RUNNING, fraction, msg);
                if (progress != ActivityProgress.NONE
                    && event.getDescriptor() instanceof FileDownloadOperationDescriptor file) {
                    String url = file.getUri().toString();
                    if (event instanceof StatusEvent status && "bytes".equalsIgnoreCase(status.getUnit())) {
                        downloads.update(url, status.getProgress(), status.getTotal());
                    } else if (event instanceof FileDownloadFinishEvent) {
                        downloads.finish(url);
                    }
                }
            });

            applyDebugConfiguration(request, build, handle);

            try (stdout; stderr) {
                build.run();
            }

            return new GradleTaskExecutionResult(handle.currentState(), 0, bufferToString(handle.getOutputBuffer()),
                bufferToString(handle.getErrorBuffer()));
        }
    }

    private List<String> buildArguments(GradleTaskExecutionRequest request) {
        List<String> args = new ArrayList<>(request.additionalArgs());

        if (request.offline()) {
            args.add("--offline");
        }

        if (request.refreshDependencies()) {
            args.add("--refresh-dependencies");
        }

        switch (request.consoleMode()) {
            case RICH -> args.add("--console=rich");
            case PLAIN -> args.add("--console=plain");
            case QUIET -> args.add("--quiet");
        }

        // system properties as -Dkey=value

        request.systemProperties().forEach((key, value) -> args.add("-D" + key + "=" + value));

        return args;
    }

    private void applyDebugConfiguration(
        GradleTaskExecutionRequest request,
        BuildLauncher build,
        ToolingGradleTaskExecutionHandle handle
    ) {
        if (!request.debug())
            return;

        int debugPort = findFreePort();

        List<String> debugJvmArgs = List.of(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:" + debugPort);
        build.addJvmArguments(debugJvmArgs);

        handle.setDebugPort(debugPort);
        handle.updateState(
            GradleTaskState.STARTING,
            "railroad.gradle.execution.debug_started",
            debugPort);
    }
}
