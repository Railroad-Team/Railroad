package dev.railroadide.railroad.ide.runconfig.defaults;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.debug.model.*;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.defaults.data.JavaApplicationRunConfigurationData;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.JavaVersion;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class JavaApplicationDebugIntegrationTest {
    @TempDir
    private Path directory;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void breakpointLocalsStepContinueAndNaturalExitCanBeRepeated() throws Exception {
        try (var fixture = fixture(false, false)) {
            Services.BREAKPOINT_SERVICE.add(fixture.source, 7);
            for (int iteration = 0; iteration < 2; iteration++) {
                var completion = fixture.type.debug(fixture.project, fixture.configuration);
                await(() -> fixture.manager().getVariables().stream().anyMatch(value -> value.name().equals("y")));
                assertTrue(fx(() -> fixture.manager().getVariables().stream()
                    .anyMatch(value -> value.name().equals("x") && value.value().equals("5"))));
                assertTrue(fx(() -> fixture.manager().getVariables().stream()
                    .anyMatch(value -> value.name().equals("y") && value.value().equals("10"))));
                fx(() -> {
                    fixture.manager().step(DebugStepKind.OVER);
                    return null;
                });
                await(() -> fixture.manager().selectedFrameProperty().get() != null
                    && fixture.manager().selectedFrameProperty().get().line() == 8);
                fx(() -> {
                    fixture.manager().resume();
                    return null;
                });
                completion.get(10, TimeUnit.SECONDS);
                fixture.assertStopped();
            }
        }
    }

    @Test
    public void stopWhileSuspendedClearsStateAndImmediatelyAllowsAnotherDebug() throws Exception {
        try (var fixture = fixture(false, false)) {
            Services.BREAKPOINT_SERVICE.add(fixture.source, 7);
            for (int iteration = 0; iteration < 2; iteration++) {
                fixture.type.debug(fixture.project, fixture.configuration);
                await(() -> fixture.manager().canStep());
                fixture.type.stop(fixture.project, fixture.configuration).get(3, TimeUnit.SECONDS);
                fixture.assertStopped();
            }
        }
    }

    @Test
    public void stopWhileAttachingCancelsBeforeTheFiveSecondDeadline() throws Exception {
        try (var fixture = fixture(true, true)) {
            var completion = fixture.type.debug(fixture.project, fixture.configuration);
            await(() -> fixture.manager().getState().get() == DebugSessionState.ATTACHING);
            fixture.type.stop(fixture.project, fixture.configuration).get(3, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> completion.get(3, TimeUnit.SECONDS));
            fixture.assertStopped();
            fixture.type.omitJdwp.set(false);
            fixture.configuration.data().setProgramArguments(new String[0]);
            fixture.type.debug(fixture.project, fixture.configuration).get(10, TimeUnit.SECONDS);
            fixture.assertStopped();
        }
    }

    @Test
    public void targetExitBeforeAttachmentCancelsRetriesAndClearsState() throws Exception {
        try (var fixture = fixture(true, false)) {
            var completion = fixture.type.debug(fixture.project, fixture.configuration);
            assertThrows(ExecutionException.class, () -> completion.get(3, TimeUnit.SECONDS));
            fixture.assertStopped();
        }
    }

    @Test
    public void failedAttachmentStopsChildAndAllowsASecondSession() throws Exception {
        try (var fixture = fixture(true, true)) {
            var completion = fixture.type.debug(fixture.project, fixture.configuration);
            assertThrows(ExecutionException.class, () -> completion.get(10, TimeUnit.SECONDS));
            fixture.assertStopped();
            fixture.type.omitJdwp.set(false);
            fixture.configuration.data().setProgramArguments(new String[0]);
            fixture.type.debug(fixture.project, fixture.configuration).get(10, TimeUnit.SECONDS);
            fixture.assertStopped();
        }
    }

    private Fixture fixture(boolean omitJdwp, boolean linger) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("module/src/main/java/demo"));
        Path source = sources.resolve("Main.java");
        Files.writeString(source, """
            package demo;
            public class Main {
                public static void main(String[] args) throws Exception {
                    if (args.length > 0) Thread.sleep(30000);
                    int x = 5;
                    int y = 10;
                    int result = x + y;
                    System.out.println(result);
                }
            }
            """);
        Path output = Files.createDirectories(directory.resolve("classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-g", "-d", output.toString(), source.toString()));
        var type = new RecordingLauncher(omitJdwp);
        var data = new JavaApplicationRunConfigurationData();
        data.setName("Java debugger fixture");
        data.setMainClass("demo.Main");
        data.setWorkingDirectory(directory);
        data.setClasspathEntries(new String[]{output.toString()});
        data.setProgramArguments(linger ? new String[]{"linger"} : new String[0]);
        data.setJdk(new JDK(Path.of(System.getProperty("java.home")), "Test JDK",
            JavaVersion.fromMajor(Runtime.version().feature())));
        var manager = new DebuggingManager[1];
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (_, method, _) -> switch (method.getName()) {
                case "getPath" -> directory;
                case "getDebuggingManager" -> manager[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        manager[0] = new DebuggingManager(project);
        return new Fixture(type, new RunConfiguration<>(type, data), project, source);
    }

    private record Fixture(
        RecordingLauncher type,
        RunConfiguration<JavaApplicationRunConfigurationData> configuration,
        Project project,
        Path source
    ) implements AutoCloseable {
        private DebuggingManager manager() {
            return project.getDebuggingManager();
        }

        private void assertStopped() throws Exception {
            assertFalse(type.isRunning(project, configuration));
            assertFalse(type.child.isAlive());
            assertTrue(Services.DEBUG_SERVICE.getActiveSession().isEmpty());
            await(() -> !manager().isActive() && manager().getState().get() == DebugSessionState.TERMINATED);
            assertTrue(fx(() -> manager().getFrames().isEmpty() && manager().getVariables().isEmpty()));
        }

        @Override
        public void close() throws Exception {
            type.stop(project, configuration).get(10, TimeUnit.SECONDS);
            Services.BREAKPOINT_SERVICE.remove(source, 7);
            if (type.child != null && type.child.isAlive()) {
                type.child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static final class RecordingLauncher extends JavaApplicationRunConfigurationType {
        private final AtomicBoolean omitJdwp;
        private volatile Process child;

        private RecordingLauncher(boolean omitJdwp) {
            this.omitJdwp = new AtomicBoolean(omitJdwp);
        }

        @Override
        protected Process startProcess(ProcessBuilder builder) throws IOException {
            // Launch a real JVM without a listener to exercise attachment failure and cancellation deterministically.
            if (omitJdwp.get()) {
                List<String> command = builder.command();
                command.removeIf((String argument) -> argument.startsWith("-agentlib:jdwp="));
            }
            child = super.startProcess(builder);
            return child;
        }
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition)) {
            if (System.nanoTime() >= deadline) {
                fail("Timed out waiting for debugger state");
            }
            Thread.sleep(20);
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }
}
