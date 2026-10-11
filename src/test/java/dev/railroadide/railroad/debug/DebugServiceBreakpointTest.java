package dev.railroadide.railroad.debug;

import dev.railroadide.railroad.debug.breakpoint.BreakpointService;
import dev.railroadide.railroad.debug.model.DebugEndpoint;
import dev.railroadide.railroad.debug.model.DebugSessionEvent;
import dev.railroadide.railroad.debug.model.DebugStopReason;
import dev.railroadide.railroad.debug.source.DependencySourceIndex;
import dev.railroadide.railroad.debug.source.SourceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DebugServiceBreakpointTest {
    @TempDir
    private Path directory;

    @Test
    public void serviceBreakpointsStopTheVmAndCanBeRemovedAndAddedDuringSession() throws Exception {
        Path source = directory.resolve("BreakpointTarget.java");
        Files.writeString(source, """
            public class BreakpointTarget {
                public static void main(String[] args) throws Exception {
                    for (int i = 0; i < 4; i++) {
                        System.in.read();
                        System.out.println("hit");
                        System.out.println("moved");
                    }
                }
            }
            """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-g", "-d", directory.toString(), source.toString()));

        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", executable).toString(),
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:0",
            "-cp", directory.toString(), "BreakpointTarget")
            .redirectErrorStream(true).start();
        var output = new LinkedBlockingQueue<String>();
        Thread.ofPlatform().daemon().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(output::add);
            } catch (Exception exception) {
                output.add(exception.toString());
            }
        });

        var debug = new DebugService();
        try {
            String address = awaitOutput(output, "Listening for transport dt_socket at address:");
            int port = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1).trim());
            var breakpoints = new BreakpointService();
            breakpoints.add(source, 5);
            var stops = new LinkedBlockingQueue<DebugSessionEvent.Suspended>();
            var resolver = new SourceResolver(List.of(directory), DependencySourceIndex.EMPTY);
            var session = debug.startSession(new DebugEndpoint("127.0.0.1", port), resolver,
                breakpoints, _ -> true, event -> {
                    if (event instanceof DebugSessionEvent.Suspended suspended) {
                        stops.add(suspended);
                    }
                }).get(10, TimeUnit.SECONDS);

            sendInput(process);
            assertStop(stops, DebugStopReason.BREAKPOINT);
            breakpoints.remove(source, 5);
            session.resume().get(10, TimeUnit.SECONDS);
            awaitOutput(output, "hit");

            // The next iteration must run through the line whose breakpoint was removed.
            sendInput(process);
            awaitOutput(output, "hit");
            assertTrue(stops.isEmpty());

            // Pause/resume provides a command-queue barrier after adding while running.
            breakpoints.add(source, 5);
            session.pause().get(10, TimeUnit.SECONDS);
            assertStop(stops, DebugStopReason.PAUSE);
            session.resume().get(10, TimeUnit.SECONDS);
            sendInput(process);
            assertStop(stops, DebugStopReason.BREAKPOINT);
            String original = Files.readString(source);
            var breakpoint = breakpoints.get(source, 5).orElseThrow();
            try (var tracking = breakpoints.trackDocument(source, original)) {
                tracking.update("\n" + original);
                assertEquals(breakpoint.id(), breakpoints.get(source, 6).orElseThrow().id());
                session.resume().get(10, TimeUnit.SECONDS);
                var movedStop = assertStop(stops, DebugStopReason.BREAKPOINT);
                assertEquals(6, session.stackFrames(movedStop.threadId()).get(10, TimeUnit.SECONDS).getFirst().line());
                session.resume().get(10, TimeUnit.SECONDS);
                awaitOutput(output, "moved");

                // On the next iteration only the new runtime line should stop the VM.
                sendInput(process);
                movedStop = assertStop(stops, DebugStopReason.BREAKPOINT);
                assertEquals(6, session.stackFrames(movedStop.threadId()).get(10, TimeUnit.SECONDS).getFirst().line());
            }
            debug.terminateActiveSession().get(10, TimeUnit.SECONDS);
            assertTrue(debug.getActiveSession().isEmpty());
        } finally {
            try {
                debug.terminateActiveSession().get(10, TimeUnit.SECONDS);
            } finally {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void sendInput(Process process) throws Exception {
        process.getOutputStream().write('x');
        process.getOutputStream().flush();
    }

    private static DebugSessionEvent.Suspended assertStop(
        BlockingQueue<DebugSessionEvent.Suspended> stops,
        DebugStopReason expected
    ) throws Exception {
        DebugSessionEvent.Suspended stop = stops.poll(10, TimeUnit.SECONDS);
        assertNotNull(stop, "The debuggee did not suspend");
        assertEquals(expected, stop.reason());
        return stop;
    }

    private static String awaitOutput(BlockingQueue<String> output, String prefix) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            String line = output.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertNotNull(line, "Missing debuggee output: " + prefix);
            if (line.startsWith(prefix))
                return line;
        }
        throw new AssertionError("Missing debuggee output: " + prefix);
    }
}
