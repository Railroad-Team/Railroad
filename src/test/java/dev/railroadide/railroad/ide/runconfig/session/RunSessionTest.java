package dev.railroadide.railroad.ide.runconfig.session;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.defaults.ShellScriptRunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.ShellScriptRunConfigurationData;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class RunSessionTest {
    @Test
    public void subscriberSeesEveryChunkExactlyOnceWhileWritersRun() throws Exception {
        var session = new RunSession(configuration(), false);
        int writers = 4;
        int chunksPerWriter = 5_000;
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int writer = 0; writer < writers; writer++) {
                int id = writer;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < chunksPerWriter; index++) {
                        session.append(RunOutput.Kind.STDOUT, id + ":" + index + "\n");
                    }

                    return null;
                }));
            }

            List<RunOutput> received = Collections.synchronizedList(new ArrayList<RunOutput>());
            start.countDown();
            Thread.sleep(5);
            Runnable unsubscribe = session.subscribe(new RecordingListener(received));
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            unsubscribe.run();

            assertEquals(session.retainedOutput(), received);
            assertEquals(writers * chunksPerWriter, received.size());
            for (int writer = 0; writer < writers; writer++) {
                String prefix = writer + ":";
                List<String> own = received.stream().map(RunOutput::text).filter(text -> text.startsWith(prefix))
                    .toList();
                for (int index = 0; index < own.size(); index++) {
                    assertEquals(prefix + index + "\n", own.get(index));
                }
            }
        }
    }

    @Test
    public void oldestOutputIsTrimmedButNewestChunkIsKept() {
        var session = new RunSession(configuration(), false, 10);
        session.append(RunOutput.Kind.STDOUT, "aaaa");
        session.append(RunOutput.Kind.STDOUT, "bbbb");
        session.append(RunOutput.Kind.STDERR, "cccc");

        assertEquals(List.of(new RunOutput(RunOutput.Kind.STDOUT, "bbbb"), new RunOutput(RunOutput.Kind.STDERR,
            "cccc")), session.retainedOutput());

        session.append(RunOutput.Kind.STDOUT, "x".repeat(50));
        assertEquals(List.of(new RunOutput(RunOutput.Kind.STDOUT, "x".repeat(50))), session.retainedOutput());
    }

    @Test
    public void finishDecidesStateOnce() {
        var succeeded = new RunSession(configuration(), false);
        assertTrue(succeeded.finish(0, null));
        assertFalse(succeeded.finish(1, new IllegalStateException("late")));
        assertEquals(RunSessionState.SUCCEEDED, succeeded.state());
        assertEquals(OptionalInt.of(0), succeeded.exitCode());

        var nonZero = new RunSession(configuration(), false);
        nonZero.finish(3, null);
        assertEquals(RunSessionState.FAILED, nonZero.state());
        assertTrue(nonZero.failureMessage().isEmpty());

        var failed = new RunSession(configuration(), false);
        failed.finish(null, new CompletionException(new IllegalStateException("Main class is not specified")));
        assertEquals(RunSessionState.FAILED, failed.state());
        assertEquals("Main class is not specified", failed.failureMessage().orElseThrow());
        assertTrue(failed.exitCode().isEmpty());

        var stopped = new RunSession(configuration(), false);
        stopped.requestStop();
        stopped.finish(143, new CancellationException());
        assertEquals(RunSessionState.STOPPED, stopped.state());
    }

    @Test
    public void stopRequestedAfterEndHasNoEffect() {
        var session = new RunSession(configuration(), false);
        session.finish(0, null);
        session.requestStop();

        assertFalse(session.isStopRequested());
        assertEquals(RunSessionState.SUCCEEDED, session.state());
    }

    @Test
    public void lateSubscriberReceivesOutputThenFinalState() {
        var session = new RunSession(configuration(), false);
        session.appendSystemLine("java Main");
        session.append(RunOutput.Kind.STDOUT, "done");
        session.finish(0, null);
        session.append(RunOutput.Kind.STDOUT, "ignored");

        List<Object> events = new ArrayList<Object>();
        session.subscribe(new RunSessionListener() {
            @Override
            public void outputAppended(RunOutput output) {
                events.add(output);
            }

            @Override
            public void sessionEnded(RunSessionState state) {
                events.add(state);
            }
        });

        assertEquals(List.of(
            new RunOutput(RunOutput.Kind.SYSTEM, "java Main\n"),
            new RunOutput(RunOutput.Kind.STDOUT, "done"),
            RunSessionState.SUCCEEDED), events);
    }

    @Test
    public void unsubscribedListenerStopsReceiving() {
        var session = new RunSession(configuration(), false);
        List<RunOutput> received = new ArrayList<RunOutput>();
        Runnable unsubscribe = session.subscribe(new RecordingListener(received));
        session.append(RunOutput.Kind.STDOUT, "one");
        unsubscribe.run();
        session.append(RunOutput.Kind.STDOUT, "two");

        assertEquals(List.of(new RunOutput(RunOutput.Kind.STDOUT, "one")), received);
    }

    @Test
    public void inputIsRecordedAndReadUntilSessionEnds() throws Exception {
        var session = new RunSession(configuration(), false);
        InputStream input = session.input(StandardCharsets.UTF_8);
        assertTrue(session.sendInput("héllo\n"));

        byte[] buffer = new byte[64];
        int read = input.read(buffer);
        assertEquals("héllo\n", new String(buffer, 0, read, StandardCharsets.UTF_8));

        var reader = CompletableFuture.supplyAsync(() -> {
            try {
                return input.read();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });

        Thread.sleep(50);
        assertFalse(reader.isDone());
        session.finish(0, null);

        assertEquals(-1, reader.get(5, TimeUnit.SECONDS));
        assertFalse(session.sendInput("late\n"));
        assertEquals(List.of(new RunOutput(RunOutput.Kind.INPUT, "héllo\n")), session.retainedOutput());
    }

    @Test
    public void connectRecordsProcessOutputAndForwardsInput() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path source = Path.of("src", "test", "resources", "run-session", "Echo.java").toAbsolutePath();
        Process process = new ProcessBuilder(java, source.toString()).start();
        var session = new RunSession(configuration(), false);
        CompletableFuture<Void> drained = session.connect(process, StandardCharsets.UTF_8);

        session.sendInput("ping\n");
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        drained.get(10, TimeUnit.SECONDS);
        session.finish(process.exitValue(), null);

        var stdout = new StringBuilder();
        var stderr = new StringBuilder();
        for (RunOutput output : session.retainedOutput()) {
            switch (output.kind()) {
                case STDOUT -> stdout.append(output.text());
                case STDERR -> stderr.append(output.text());
                default -> {
                }
            }
        }

        assertEquals("echo: ping", stdout.toString().strip());
        assertTrue(stderr.toString().strip().endsWith("to stderr"), stderr.toString());
        assertEquals(RunSessionState.SUCCEEDED, session.state());
    }

    @Test
    public void stdoutStreamRecordsDecodedText() throws Exception {
        var session = new RunSession(configuration(), false);
        try (OutputStream stdout = session.stdout(StandardCharsets.UTF_8)) {
            stdout.write("ok".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(List.of(new RunOutput(RunOutput.Kind.STDOUT, "ok")), session.retainedOutput());
    }

    public static RunConfiguration<ShellScriptRunConfigurationData> configuration() {
        var data = new ShellScriptRunConfigurationData();
        data.setName("Session fixture");
        return new RunConfiguration<>(new ShellScriptRunConfigurationType(), data);
    }

    private record RecordingListener(List<RunOutput> received) implements RunSessionListener {
        @Override
        public void outputAppended(RunOutput output) {
            received.add(output);
        }

        @Override
        public void sessionEnded(RunSessionState state) {
        }
    }
}
