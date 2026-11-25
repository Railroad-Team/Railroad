package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.EditorMain;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.scene.image.WritableImage;
import dev.railroadide.railroad.ide.runconfig.session.RunOutput;
import dev.railroadide.railroad.ide.runconfig.session.RunSession;
import dev.railroadide.railroad.ide.runconfig.session.RunSessionManager;
import dev.railroadide.railroad.ide.runconfig.session.RunSessionState;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.JavaVersion;
import javafx.application.Platform;
import org.gradle.tooling.GradleConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class GradleRunConfigurationLifecycleTest {
    @TempDir
    private Path directory;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        WorkspaceModes.initialize();
        var ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready::countDown);
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void buildOutputIsRecordedInRunSession() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'output'");
        Files.writeString(directory.resolve("build.gradle"), """
            tasks.register('speak') {
                doLast {
                    println 'hello from the build'
                    System.err.println 'warning from the build'
                }
            }
            """);
        var type = type();
        var configuration = configuration(type, "speak");
        Project project = project();

        type.run(project, configuration).get(60, TimeUnit.SECONDS);

        RunSession session = project.getRunSessions().sessions().getLast();
        assertSame(configuration, session.configuration());
        assertEquals(RunSessionState.SUCCEEDED, session.state());
        String stdout = text(session, RunOutput.Kind.STDOUT);
        String stderr = text(session, RunOutput.Kind.STDERR);
        assertTrue(stdout.contains("hello from the build"), stdout);
        assertTrue(stderr.contains("warning from the build") || stdout.contains("warning from the build"),
            stdout + stderr);
        assertEquals("gradle speak\n", text(session, RunOutput.Kind.SYSTEM));
    }

    @Test
    public void stoppedBuildEndsSessionAsStopped() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'stopped'");
        Files.writeString(directory.resolve("build.gradle"), """
            tasks.register('wait') {
                doLast {
                    file('started').text = 'ready'
                    Thread.sleep(30000)
                }
            }
            """);
        var type = type();
        var configuration = configuration(type, "wait");
        Project project = project();
        var completion = type.run(project, configuration);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!Files.exists(directory.resolve("started")) && !completion.isDone()
                && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(directory.resolve("started")));
            type.stop(project, configuration).get(15, TimeUnit.SECONDS);

            assertEquals(RunSessionState.STOPPED, project.getRunSessions().sessions().getLast().state());
        } finally {
            type.stop(project, configuration).get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void buildFailurePreservesCauseAndAllowsRerun() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'lifecycle'");
        Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }");
        var type = type();
        var configuration = configuration(type, "compileJava");
        Project project = project();
        for (int attempt = 0; attempt < 2; attempt++) {
            var completion = type.debug(project, configuration);
            assertTrue(type.isRunning(project, configuration));
            var failure = assertThrows(ExecutionException.class, () -> completion.get(60, TimeUnit.SECONDS));
            assertTrue(messages(failure).contains("Only JavaExec and Test"), messages(failure));
            assertFalse(type.isRunning(project, configuration));
            assertTrue(Services.DEBUG_SERVICE.getActiveSession().isEmpty());
            flushFx();
            assertFalse(project.getDebuggingManager().isActive());
        }
    }

    @Test
    public void stopDoesNotBlockJavaFxDuringConnectorStartup() throws Exception {
        var connecting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var type = new GradleRunConfigurationType(() -> {
            connecting.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Connector was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
            return GradleConnector.newConnector()
                .useInstallation(new File(System.getProperty("railroad.test.gradleHome")));
        });
        var configuration = configuration(type, "help");
        Project project = project();
        var completion = type.debug(project, configuration);
        try {
            assertTrue(connecting.await(5, TimeUnit.SECONDS));
            var stop = new FutureTask<CompletableFuture<Void>>(() -> type.stop(project, configuration));
            Platform.runLater(stop);
            CompletableFuture<Void> cleaned = stop.get(2, TimeUnit.SECONDS);
            assertFalse(cleaned.isDone());
            release.countDown();
            cleaned.get(10, TimeUnit.SECONDS);
            assertTrue(completion.isCompletedExceptionally());
            assertFalse(type.isRunning(project, configuration));
            assertTrue(Services.DEBUG_SERVICE.getActiveSession().isEmpty());
        } finally {
            release.countDown();
            type.stop(project, configuration).get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    public void stopWhileWaitingForChildJvmCleansUpAndAllowsRun() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'stop-fixture'");
        Files.writeString(directory.resolve("build.gradle"), """
            plugins { id 'java' }
            tasks.register('delay') {
                doLast {
                    file('started').text = 'ready'
                    Thread.sleep(30000)
                }
            }
            tasks.register('slow', JavaExec) {
                dependsOn 'delay'
                mainClass = 'Missing'
            }
            """);
        var type = type();
        var configuration = configuration(type, "slow");
        Project project = project();
        var completion = type.debug(project, configuration);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!Files.exists(directory.resolve("started")) && !completion.isDone()
                && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(directory.resolve("started")));
            type.stop(project, configuration).get(15, TimeUnit.SECONDS);
            assertTrue(completion.isCompletedExceptionally());
            assertFalse(type.isRunning(project, configuration));
            assertTrue(Services.DEBUG_SERVICE.getActiveSession().isEmpty());
            flushFx();
            assertFalse(project.getDebuggingManager().isActive());
            configuration.data().setTask("help");
            type.run(project, configuration).get(60, TimeUnit.SECONDS);
            assertFalse(type.isRunning(project, configuration));
        } finally {
            type.stop(project, configuration).get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void editorMainRunsAndDebugsWithTheOwningSourceSetsRuntimeClasspath() throws Exception {
        Files.writeString(directory.resolve("settings.gradle"), "include 'app', 'library', 'unrelated'");
        Path app = Files.createDirectories(directory.resolve("app/code/sample"));
        Files.writeString(directory.resolve("app/build.gradle"), """
            plugins { id 'java' }
            sourceSets.main.java.setSrcDirs(['code'])
            dependencies { runtimeOnly project(':library') }
            """);
        Path main = app.resolve("Main.java");
        Files.writeString(main, """
            package sample;
            public class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(Class.forName("OnlyAtRuntime").getSimpleName());
                    System.out.println(Main.class.getResource("/app-resource.txt") != null);
                }
            }
            """);
        Files.writeString(
            Files.createDirectories(directory.resolve("app/src/main/resources")).resolve("app-resource.txt"),
            "resource");
        Files.writeString(
            Files.createDirectories(directory.resolve("library/src/main/java")).resolve("OnlyAtRuntime.java"),
            "public class OnlyAtRuntime {}");
        Files.writeString(directory.resolve("library/build.gradle"), "plugins { id 'java' }");
        Files.writeString(Files.createDirectories(directory.resolve("unrelated/src/main/java")).resolve("Broken.java"),
            "invalid");
        Files.writeString(directory.resolve("unrelated/build.gradle"), "plugins { id 'java' }");
        var project = new RailroadProject(directory, "Editor launch", new WritableImage(1, 1));
        assertTrue(EditorMain.supports(project, main));
        var target = EditorMain.find(Files.readString(main), "Main.java").getFirst();
        var config = target.configuration(project, main);
        assertSame(config, target.configuration(project, main));
        var data = assertInstanceOf(GradleRunConfigurationData.class, config.data());
        assertEquals("app/code/sample/Main.java", data.getMainSource());
        var type = type();
        var configuration = new RunConfiguration<>(config.uuid(), type, data);
        data.setJavaHome(new JDK(Path.of(System.getProperty("java.home")), "Test JDK", JavaVersion.fromMajor(25)));
        configuration.run(project).get(60, TimeUnit.SECONDS);
        String output = text(project.getRunSessions().sessions().getLast(), RunOutput.Kind.STDOUT);
        assertTrue(output.contains("OnlyAtRuntime"), output);
        assertTrue(output.contains("true"), output);
        Services.BREAKPOINT_SERVICE.add(main, 4);
        try {
            var completion = configuration.debug(project);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!project.getDebuggingManager().isPaused() && !completion.isDone() && System.nanoTime() < deadline) {
                flushFx();
                Thread.sleep(25);
            }
            if (completion.isDone()) {
                completion.join();
            }
            var session = project.getRunSessions().sessions().getLast();
            assertTrue(project.getDebuggingManager().isPaused(),
                () -> "Debugger did not suspend: " + session.retainedOutput());
            Platform.runLater(project.getDebuggingManager()::resume);
            completion.get(30, TimeUnit.SECONDS);
        } finally {
            Services.BREAKPOINT_SERVICE.remove(main, 4);
            configuration.stop(project).get(30, TimeUnit.SECONDS);
        }
    }

    private GradleRunConfigurationType type() {
        return new GradleRunConfigurationType(() -> GradleConnector.newConnector()
            .useInstallation(new File(System.getProperty("railroad.test.gradleHome"))));
    }

    private RunConfiguration<GradleRunConfigurationData> configuration(GradleRunConfigurationType type, String task) {
        var data = new GradleRunConfigurationData();
        data.setTask(task);
        data.setGradleProjectPath(directory);
        data.setJavaHome(new JDK(Path.of(System.getProperty("java.home")), "Test JDK", JavaVersion.fromMajor(25)));
        return new RunConfiguration<>(type, data);
    }

    private Project project() {
        var manager = new DebuggingManager[1];
        var sessions = new RunSessionManager();
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPath" -> directory;
                case "getDebuggingManager" -> manager[0];
                case "hasFacet" -> false;
                case "getRunSessions" -> sessions;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        manager[0] = new DebuggingManager(project);
        return project;
    }

    private static void flushFx() throws Exception {
        var flushed = new CompletableFuture<Void>();
        Platform.runLater(() -> flushed.complete(null));
        flushed.get(10, TimeUnit.SECONDS);
    }

    private static String text(RunSession session, RunOutput.Kind kind) {
        return session.retainedOutput().stream()
            .filter(output -> output.kind() == kind)
            .map(RunOutput::text)
            .collect(Collectors.joining());
    }

    private static String messages(Throwable failure) {
        return failure == null ? "" : failure.getMessage() + "\n" + messages(failure.getCause());
    }
}
