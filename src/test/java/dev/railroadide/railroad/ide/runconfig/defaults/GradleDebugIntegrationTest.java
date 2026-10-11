package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.debug.DebugService;
import dev.railroadide.railroad.debug.breakpoint.BreakpointService;
import dev.railroadide.railroad.debug.jdi.JdiDebugSession;
import dev.railroadide.railroad.debug.model.*;
import dev.railroadide.railroad.debug.source.DependencySourceIndex;
import dev.railroadide.railroad.debug.source.SourceResolver;
import org.gradle.tooling.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class GradleDebugIntegrationTest {
    @TempDir
    private Path directory;

    @Test
    public void javaExecSupportsBreakpointsLocalsSteppingLiveChangesAndConsecutiveSessions() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'debug-fixture'");
        Files.writeString(directory.resolve("build.gradle"), """
            plugins { id 'application' }
            application { mainClass = 'Main' }
            run { doFirst { Thread.sleep(5500) } }
            tasks.withType(JavaCompile).configureEach { options.debugOptions.debugLevel = 'source,lines,vars' }
            """);
        Path sources = Files.createDirectories(directory.resolve("src/main/java"));
        Path source = sources.resolve("Main.java");
        Files.writeString(source, """
            public class Main {
                public static void main(String[] args) {
                    int x = 5;
                    int y = 10;
                    int result = x + y;
                    System.out.println(result);
                    System.out.println("done");
                }
            }
            """);
        for (int iteration = 0; iteration < 2; iteration++) {
            var breakpoints = new BreakpointService();
            breakpoints.add(source, 5);
            try (var build = start(":run")) {
                var stops = new LinkedBlockingQueue<DebugSessionEvent.Suspended>();
                var session = build.attach(sources, breakpoints, stops);
                var stop = stops.poll(60, TimeUnit.SECONDS);
                assertNotNull(stop, build.output.toString());
                assertEquals(DebugStopReason.BREAKPOINT, stop.reason());
                var frame = session.stackFrames(stop.threadId()).get(10, TimeUnit.SECONDS).getFirst();
                var variables = session.variables(frame.id()).get(10, TimeUnit.SECONDS);
                assertTrue(variables.stream().anyMatch(value -> value.name().equals("x") && value.value().equals("5")));
                assertTrue(
                    variables.stream().anyMatch(value -> value.name().equals("y") && value.value().equals("10")));
                session.step(stop.threadId(), DebugStepKind.OVER).get(10, TimeUnit.SECONDS);
                assertNotNull(stops.poll(10, TimeUnit.SECONDS));
                breakpoints.add(source, 7);
                session.resume().get(10, TimeUnit.SECONDS);
                assertEquals(DebugStopReason.BREAKPOINT, stops.poll(10, TimeUnit.SECONDS).reason());
                session.resume().get(10, TimeUnit.SECONDS);
                build.completion.get(60, TimeUnit.SECONDS);
                assertTrue(build.output.toString().contains("15"));
            }
        }
    }

    @Test
    public void testTaskUsesOneWorkerAndRunsAgainWhenPreviouslySuccessful() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'debug-tests'");
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(
                path -> Path.of(path).getFileName().toString().matches("(junit-.*|opentest4j-.*|apiguardian-.*)\\.jar"))
            .map(path -> "'" + path.replace("\\", "/").replace("'", "\\'") + "'")
            .collect(Collectors.joining(","));
        Files.writeString(directory.resolve("build.gradle"), """
            plugins { id 'java' }
            dependencies { testImplementation files([%s]) }
            test {
                useJUnitPlatform()
                maxParallelForks = 3
                forkEvery = 1
                doFirst {
                    assert maxParallelForks == 1
                    assert forkEvery == 0
                }
            }
            """.formatted(classpath));
        Path sources = Files.createDirectories(directory.resolve("src/test/java"));
        Path source = sources.resolve("ExampleTest.java");
        Files.writeString(source, """
            public class ExampleTest {
                @org.junit.jupiter.api.Test
                void addition() {
                    int result = 5 + 10;
                    org.junit.jupiter.api.Assertions.assertEquals(15, result);
                }
            }
            """);
        for (int iteration = 0; iteration < 2; iteration++) {
            var breakpoints = new BreakpointService();
            breakpoints.add(source, 5);
            try (var build = start(":test")) {
                var stops = new LinkedBlockingQueue<DebugSessionEvent.Suspended>();
                var session = build.attach(sources, breakpoints, stops);
                assertNotNull(stops.poll(60, TimeUnit.SECONDS), build.output.toString());
                session.resume().get(10, TimeUnit.SECONDS);
                build.completion.get(60, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void unsupportedTaskFailsBeforeAttachmentWithoutWaitingForTimeout() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'unsupported'");
        Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }");
        try (var build = start(":compileJava")) {
            var debug = new DebugService();
            var attachment = debug.startSession(new DebugEndpoint("127.0.0.1", build.port),
                new SourceResolver(List.of(directory), DependencySourceIndex.EMPTY), new BreakpointService(),
                _ -> true, _ -> {
                }, Duration.ofMinutes(1), build.completion::isDone);
            var failure = assertThrows(ExecutionException.class, () -> build.completion.get(60, TimeUnit.SECONDS));
            assertTrue(messages(failure).contains("Only JavaExec and Test"), messages(failure));
            assertThrows(ExecutionException.class, () -> attachment.get(3, TimeUnit.SECONDS));
            assertTrue(debug.getActiveSession().isEmpty());
        }
    }

    private static String messages(Throwable failure) {
        return failure == null ? "" : failure.getMessage() + "\n" + messages(failure.getCause());
    }

    private Build start(String task) throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path script = Files.createTempFile(directory, "debug-", ".init.gradle");
        Files.writeString(script, GradleRunConfigurationType.debugInitScript(task, port));
        var connection = GradleConnector.newConnector().forProjectDirectory(directory.toFile())
            .useInstallation(new File(System.getProperty("railroad.test.gradleHome"))).connect();
        var build = new Build(port, script, connection);
        connection.newBuild().forTasks(task).setJavaHome(new File(System.getProperty("java.home")))
            .withArguments("--init-script", script.toString(), "--no-configuration-cache", "--offline")
            .withCancellationToken(build.cancellation.token())
            .setStandardOutput(build.output).setStandardError(build.output)
            .run(new ResultHandler<>() {
                public void onComplete(Void result) {
                    build.completion.complete(null);
                }
                public void onFailure(GradleConnectionException failure) {
                    build.completion.completeExceptionally(failure);
                }
            });
        return build;
    }

    private static final class Build implements AutoCloseable {
        private final int port;
        private final Path script;
        private final ProjectConnection connection;
        private final CancellationTokenSource cancellation = GradleConnector.newCancellationTokenSource();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private JdiDebugSession session;

        private Build(int port, Path script, ProjectConnection connection) {
            this.port = port;
            this.script = script;
            this.connection = connection;
        }

        private JdiDebugSession attach(
            Path sources,
            BreakpointService breakpoints,
            BlockingQueue<DebugSessionEvent.Suspended> stops
        ) throws Exception {
            try {
                session = new DebugService().startSession(new DebugEndpoint("127.0.0.1", port),
                    new SourceResolver(List.of(sources), DependencySourceIndex.EMPTY), breakpoints, _ -> true,
                    event -> {
                        if (event instanceof DebugSessionEvent.Suspended suspended) {
                            stops.add(suspended);
                        }
                    },
                    Duration.ofMinutes(1), () -> stopped.get() || completion.isDone()).get(65, TimeUnit.SECONDS);
                return session;
            } catch (Exception failure) {
                throw new AssertionError(output.toString() + "\n"
                    + completion.handle((result, error) -> messages(error)).getNow("Build still running"), failure);
            }
        }

        public void close() throws Exception {
            stopped.set(true);
            cancellation.cancel();
            try {
                if (session != null) {
                    session.terminate().get(10, TimeUnit.SECONDS);
                }
            } finally {
                connection.close();
                Files.deleteIfExists(script);
            }
        }
    }
}
