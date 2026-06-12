package dev.railroadide.railroad.vcs.git.execution;

import dev.railroadide.railroad.vcs.git.GitCommand;
import dev.railroadide.railroad.vcs.git.GitLog;
import dev.railroadide.railroad.vcs.git.execution.progress.GitCancellationToken;
import dev.railroadide.railroad.vcs.git.execution.progress.GitResultCaptureMode;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Executes git commands and captures process output and status.
 */
public class GitProcessRunner {
    private static final Path DEFAULT_GIT_EXECUTABLE = Path.of("git");

    @Getter
    @Setter
    private Path gitExecutable;

    /**
     * Creates a runner using the supplied git executable path.
     *
     * @param gitExecutable path to the git binary
     */
    public GitProcessRunner(Path gitExecutable) {
        this.gitExecutable = gitExecutable;
    }

    /**
     * Executes a git command and returns captured process output.
     *
     * @param command command definition to execute
     * @param listener optional output listener
     * @param token optional cancellation token
     * @param captureMode stdout capture mode
     * @return execution result containing exit status and captured output
     * @throws GitExecutionException when process startup or IO handling fails
     */
    public GitResult run(GitCommand command, GitOutputListener listener, GitCancellationToken token, GitResultCaptureMode captureMode) throws GitExecutionException {
        List<String> stdoutChunks = Collections.synchronizedList(new ArrayList<>());
        List<String> stderrChunks = Collections.synchronizedList(new ArrayList<>());

        long startNanos = System.nanoTime();

        Process processRef = null;
        ExecutorService ioPool = Executors.newFixedThreadPool(2, runnable -> {
            var thread = new Thread(runnable, "GitProcessRunner-IO");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Path executable = resolveGitExecutable();
            String[] cmd = buildCommand(executable, command.arguments());
            GitLog.LOGGER.debug("Executing git command: {}", String.join(" ", cmd));
            var processBuilder = new ProcessBuilder(cmd);
            if (command.workingDirectory() != null) {
                processBuilder.directory(command.workingDirectory().toFile());
            }

            processBuilder.environment().putAll(command.environment());
            processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0"); // Disable git prompts

            final Process process = processBuilder.start();
            processRef = process;

            Consumer<String> stdoutSink = line -> {
                stdoutChunks.add(line);
                if (listener != null && command.streamStdoutToListener()) {
                    if (captureMode == GitResultCaptureMode.NULL_RECORDS) {
                        listener.onStdoutRecord(line);
                    } else {
                        listener.onStdout(line);
                    }
                }
            };

            Future<?> stdoutTask = ioPool.submit(() ->
                readOutput(process.getInputStream(), captureMode, stdoutSink)
            );

            Future<?> stderrTask = ioPool.submit(() ->
                readOutput(process.getErrorStream(), GitResultCaptureMode.TEXT_LINES, line -> {
                    stderrChunks.add(line);
                    if (listener != null) {
                        listener.onStderr(line);
                    }
                })
            );

            boolean cancelled = false;
            boolean timedOut = false;

            long timeoutMillis = command.timeoutMillis();
            long deadlineNanos = timeoutMillis <= 0
                ? Long.MAX_VALUE
                : System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

            while (true) {
                if (process.waitFor(1, TimeUnit.SECONDS))
                    break; // Process finished

                if (token != null && token.isCancellationRequested()) {
                    cancelled = true;
                    break;
                }

                if (System.nanoTime() >= deadlineNanos) {
                    timedOut = true;
                    break;
                }
            }

            if (cancelled || timedOut) {
                process.toHandle().descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }

            int exitCode;
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException stillRunning) {
                process.waitFor(2, TimeUnit.SECONDS);
                exitCode = process.isAlive() ? (cancelled ? -2 : (timedOut ? -3 : -1)) : process.exitValue();
            }

            try {
                stdoutTask.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }

            try {
                stderrTask.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }

            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            return new GitResult(
                exitCode,
                List.copyOf(stdoutChunks),
                List.copyOf(stderrChunks),
                timedOut,
                cancelled,
                duration
            );
        } catch (Exception exception) {
            throw new GitExecutionException("Failed to execute git command", exception);
        } finally {
            if (processRef != null && processRef.isAlive()) {
                processRef.destroy();
                try {
                    processRef.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }

                if (processRef.isAlive()) {
                    processRef.destroyForcibly();
                }
            }
            ioPool.shutdownNow();
        }
    }

    private static void readOutput(InputStream input, GitResultCaptureMode captureMode, Consumer<String> onLine) {
        if (captureMode == GitResultCaptureMode.NULL_RECORDS) {
            readNullRecords(input, onLine);
            return;
        }

        if (captureMode == GitResultCaptureMode.TEXT_WHOLE) {
            readWholeText(input, onLine);
            return;
        }

        readLines(input, onLine);
    }

    private static void readWholeText(InputStream input, Consumer<String> onLine) {
        byte[] buffer = new byte[4096];
        var outputBuffer = new ByteArrayOutputStream();

        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                outputBuffer.write(buffer, 0, read);
            }

            if (outputBuffer.size() > 0) {
                var output = outputBuffer.toString(StandardCharsets.UTF_8);
                onLine.accept(output);
            }
        } catch (IOException ignored) {
            // Process is destroyed or stream closed
        }
    }

    private static void readNullRecords(InputStream input, Consumer<String> onRecord) {
        byte[] buffer = new byte[4096];
        var recordBuffer = new ByteArrayOutputStream();

        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    byte value = buffer[i];
                    if (value == 0) {
                        emitRecord(recordBuffer, onRecord);
                    } else {
                        recordBuffer.write(value);
                    }
                }
            }

            if (recordBuffer.size() > 0) {
                emitRecord(recordBuffer, onRecord);
            }
        } catch (IOException ignored) {
            // Process is destroyed or stream closed
        }
    }

    private static void readLines(InputStream input, Consumer<String> onLine) {
        byte[] buffer = new byte[4096];
        var lineBuffer = new ByteArrayOutputStream();

        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    byte value = buffer[i];
                    if (value == '\n' || value == '\r') {
                        emitLine(lineBuffer, onLine);
                    } else {
                        lineBuffer.write(value);
                    }
                }
            }

            if (lineBuffer.size() > 0) {
                emitLine(lineBuffer, onLine);
            }
        } catch (IOException ignored) {
            // Process is destroyed or stream closed
        }
    }

    private static void emitLine(ByteArrayOutputStream lineBuffer, Consumer<String> onLine) {
        byte[] lineBytes = lineBuffer.toByteArray();
        lineBuffer.reset();

        int length = lineBytes.length;
        if (length > 0 && lineBytes[length - 1] == '\r') {
            length--;
        }

        var line = new String(lineBytes, 0, length, StandardCharsets.UTF_8);
        if (!line.isEmpty()) {
            onLine.accept(line);
        }
    }

    private static void emitRecord(ByteArrayOutputStream recordBuffer, Consumer<String> onRecord) {
        var record = recordBuffer.toString(StandardCharsets.UTF_8);
        recordBuffer.reset();
        if (!record.isEmpty()) {
            onRecord.accept(record);
        }
    }

    private static String[] buildCommand(Path gitExecutable, Collection<String> args) {
        String[] command = new String[args.size() + 1];
        command[0] = gitExecutable.toString();
        int index = 1;
        for (String arg : args) {
            if (arg == null) {
                arg = "";
            }

            command[index++] = arg;
        }

        return command;
    }

    private Path resolveGitExecutable() {
        if (gitExecutable != null) {
            return gitExecutable;
        }

        GitLog.LOGGER.warn("Git executable path is not configured. Falling back to '{}' from PATH.", DEFAULT_GIT_EXECUTABLE);
        gitExecutable = DEFAULT_GIT_EXECUTABLE;
        return DEFAULT_GIT_EXECUTABLE;
    }
}
