package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
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
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPath" -> directory;
                case "getDebuggingManager" -> manager[0];
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

    private static String messages(Throwable failure) {
        return failure == null ? "" : failure.getMessage() + "\n" + messages(failure.getCause());
    }
}
