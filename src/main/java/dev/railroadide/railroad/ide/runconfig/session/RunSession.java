package dev.railroadide.railroad.ide.runconfig.session;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.utility.DecodingOutputStream;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Output, input and outcome of one launch of a run configuration.
 * <p>
 * Run configuration types record what their build and process write, and finish the session once that output has
 * been drained. The session keeps the most recent output, up to a fixed number of characters, so a view opened later
 * can replay it. All methods are thread-safe.
 */
public final class RunSession {
    /**
     * Characters of output retained for replay before the oldest chunks are discarded.
     */
    public static final int DEFAULT_RETAINED_CHARS = 2_000_000;

    private static final InputChunk END_OF_INPUT = new InputChunk(null);

    private final RunConfiguration<?> configuration;
    private final boolean debug;
    private final int retainedCharLimit;
    private final Object lock = new Object();
    private final ArrayDeque<RunOutput> retained = new ArrayDeque<>();
    private final List<RunSessionListener> listeners = new ArrayList<>();
    private final LinkedBlockingQueue<InputChunk> pendingInput = new LinkedBlockingQueue<>();
    private long retainedChars;
    private boolean stopRequested;
    private RunSessionState state = RunSessionState.RUNNING;
    private @Nullable Integer exitCode;
    private @Nullable String failureMessage;

    /**
     * Creates a running session that retains {@link #DEFAULT_RETAINED_CHARS} characters of output.
     *
     * @param configuration the configuration being launched
     * @param debug whether the configuration is launched under the debugger
     */
    public RunSession(RunConfiguration<?> configuration, boolean debug) {
        this(configuration, debug, DEFAULT_RETAINED_CHARS);
    }

    /**
     * Creates a running session with a custom output retention limit.
     *
     * @param configuration the configuration being launched
     * @param debug whether the configuration is launched under the debugger
     * @param retainedCharLimit characters of output kept for replay; the newest chunk is always kept
     */
    public RunSession(RunConfiguration<?> configuration, boolean debug, int retainedCharLimit) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.debug = debug;
        if (retainedCharLimit <= 0)
            throw new IllegalArgumentException("retainedCharLimit must be positive");

        this.retainedCharLimit = retainedCharLimit;
    }

    /**
     * Returns the charset child processes use for their standard streams when they are not attached to a terminal.
     *
     * @return the platform's native charset, or the default charset when it is unavailable
     */
    public static Charset processCharset() {
        String name = System.getProperty("native.encoding");
        if (name == null)
            return Charset.defaultCharset();

        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException _) {
            return Charset.defaultCharset();
        }
    }

    /**
     * Returns the configuration this session launched.
     *
     * @return the launched configuration
     */
    public RunConfiguration<?> configuration() {
        return configuration;
    }

    /**
     * Tests whether the configuration was launched under the debugger.
     *
     * @return whether this is a debug session
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Returns the current state.
     *
     * @return the session state
     */
    public RunSessionState state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Returns the exit code the session finished with.
     *
     * @return the exit code, or empty while running or when the run had no process exit code
     */
    public OptionalInt exitCode() {
        synchronized (lock) {
            return exitCode == null ? OptionalInt.empty() : OptionalInt.of(exitCode);
        }
    }

    /**
     * Returns a description of the failure the session finished with.
     *
     * @return the failure message, or empty when the session did not finish with a failure
     */
    public Optional<String> failureMessage() {
        synchronized (lock) {
            return Optional.ofNullable(failureMessage);
        }
    }

    /**
     * Records output. Empty text and output arriving after the session has ended are ignored.
     *
     * @param kind where the text came from
     * @param text the text to record
     */
    public void append(RunOutput.Kind kind, String text) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        if (text.isEmpty())
            return;

        var output = new RunOutput(kind, text);
        synchronized (lock) {
            if (state.isTerminal())
                return;

            retained.addLast(output);
            retainedChars += text.length();
            while (retainedChars > retainedCharLimit && retained.size() > 1) {
                retainedChars -= retained.removeFirst().text().length();
            }
            for (RunSessionListener listener : listeners) {
                listener.outputAppended(output);
            }
        }
    }

    /**
     * Records a Railroad message on its own line.
     *
     * @param line the message, with or without a trailing line break
     */
    public void appendSystemLine(String line) {
        Objects.requireNonNull(line, "line");
        append(RunOutput.Kind.SYSTEM, line.endsWith("\n") ? line : line + "\n");
    }

    /**
     * Returns the output recorded so far that is still retained.
     *
     * @return the retained output, oldest first
     */
    public List<RunOutput> retainedOutput() {
        synchronized (lock) {
            return List.copyOf(retained);
        }
    }

    /**
     * Replays retained output to the listener, then delivers new output and the final state as they happen. A listener
     * subscribed after the session ended receives the retained output followed by the final state.
     *
     * @param listener the listener to notify
     * @return an action that stops further notifications
     */
    public Runnable subscribe(RunSessionListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lock) {
            retained.forEach(listener::outputAppended);
            if (state.isTerminal()) {
                listener.sessionEnded(state);
            } else {
                listeners.add(listener);
            }
        }
        return () -> {
            synchronized (lock) {
                listeners.remove(listener);
            }
        };
    }

    /**
     * Creates a stream that records decoded bytes as standard output. Close it once the source is exhausted so a
     * trailing incomplete character is flushed.
     *
     * @param charset charset the bytes are encoded in
     * @return a stream recording into this session
     */
    public OutputStream stdout(Charset charset) {
        return new DecodingOutputStream(charset, text -> append(RunOutput.Kind.STDOUT, text));
    }

    /**
     * Creates a stream that records decoded bytes as standard error. Close it once the source is exhausted so a
     * trailing incomplete character is flushed.
     *
     * @param charset charset the bytes are encoded in
     * @return a stream recording into this session
     */
    public OutputStream stderr(Charset charset) {
        return new DecodingOutputStream(charset, text -> append(RunOutput.Kind.STDERR, text));
    }

    /**
     * Sends text to the process's standard input and records it as {@link RunOutput.Kind#INPUT}.
     *
     * @param text the text to send, including any line break the process expects
     * @return whether the text was accepted; {@code false} once the session has ended
     */
    public boolean sendInput(String text) {
        Objects.requireNonNull(text, "text");
        synchronized (lock) {
            if (state.isTerminal())
                return false;

            append(RunOutput.Kind.INPUT, text);
            if (!text.isEmpty()) {
                pendingInput.add(new InputChunk(text));
            }
            return true;
        }
    }

    /**
     * Returns a stream of the text sent with {@link #sendInput(String)}. Reads block until input arrives and report
     * end of stream once the session ends. The session supports a single reader.
     *
     * @param charset charset to encode the sent text with
     * @return the standard input stream for the launched process or build
     */
    public InputStream input(Charset charset) {
        return new QueuedInput(Objects.requireNonNull(charset, "charset"));
    }

    /**
     * Connects a started process to this session: its standard output and error are recorded, and sent input is
     * forwarded to it. The process must have been started with piped standard streams.
     *
     * @param process the started process
     * @param charset charset of the process's standard streams
     * @return a future that completes once both output streams have been fully recorded
     */
    public CompletableFuture<Void> connect(Process process, Charset charset) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(charset, "charset");
        String name = "run-" + process.pid();
        CompletableFuture<Void> out = pump(process.getInputStream(), stdout(charset), name + "-stdout");
        CompletableFuture<Void> err = pump(process.getErrorStream(), stderr(charset), name + "-stderr");
        InputStream input = input(charset);
        OutputStream processInput = process.getOutputStream();
        Thread.ofPlatform().daemon().name(name + "-stdin").start(() -> forward(input, processInput));
        return CompletableFuture.allOf(out, err);
    }

    /**
     * Marks that the user asked the run to stop, so that finishing it reports {@link RunSessionState#STOPPED}.
     */
    public void requestStop() {
        synchronized (lock) {
            if (!state.isTerminal()) {
                stopRequested = true;
            }
        }
    }

    /**
     * Tests whether the user asked the run to stop.
     *
     * @return whether a stop was requested
     */
    public boolean isStopRequested() {
        synchronized (lock) {
            return stopRequested;
        }
    }

    /**
     * Ends the session. A requested stop takes precedence; otherwise a failure or non-zero exit code means
     * {@link RunSessionState#FAILED}. Only the first call has an effect.
     *
     * @param exitCode the process exit code, or {@code null} when none applies
     * @param failure the failure the run ended with, or {@code null}
     * @return whether this call ended the session
     */
    public boolean finish(@Nullable Integer exitCode, @Nullable Throwable failure) {
        synchronized (lock) {
            if (state.isTerminal())
                return false;

            this.exitCode = exitCode;
            if (stopRequested) {
                state = RunSessionState.STOPPED;
            } else if (failure != null || exitCode != null && exitCode != 0) {
                state = RunSessionState.FAILED;
            } else {
                state = RunSessionState.SUCCEEDED;
            }
            failureMessage = failure == null ? null : describe(failure);
            pendingInput.add(END_OF_INPUT);
            for (RunSessionListener listener : listeners) {
                listener.sessionEnded(state);
            }
            listeners.clear();
            return true;
        }
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static CompletableFuture<Void> pump(InputStream source, OutputStream target, String name) {
        var drained = new CompletableFuture<Void>();
        Thread.ofPlatform().daemon().name(name).start(() -> {
            try (source; target) {
                source.transferTo(target);
            } catch (IOException _) {
                // The process closed the stream; whatever was read has been recorded.
            } finally {
                drained.complete(null);
            }
        });
        return drained;
    }

    private static void forward(InputStream input, OutputStream processInput) {
        var buffer = new byte[1024];
        try (input; processInput) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                processInput.write(buffer, 0, read);
                processInput.flush();
            }
        } catch (IOException _) {
            // The process exited or closed its standard input.
        }
    }

    private record InputChunk(@Nullable String text) {
    }

    private final class QueuedInput extends InputStream {
        private final Charset charset;
        private byte[] current = new byte[0];
        private int position;
        private volatile boolean ended;

        private QueuedInput(Charset charset) {
            this.charset = charset;
        }

        @Override
        public synchronized int read() throws IOException {
            var single = new byte[1];
            return read(single, 0, 1) == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0)
                return 0;

            while (position >= current.length) {
                if (ended)
                    return -1;

                InputChunk next;
                try {
                    next = pendingInput.take();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while waiting for run input");
                }
                if (next.text() == null) {
                    ended = true;
                    return -1;
                }
                current = next.text().getBytes(charset);
                position = 0;
            }

            int count = Math.min(length, current.length - position);
            System.arraycopy(current, position, bytes, offset, count);
            position += count;
            return count;
        }

        @Override
        public synchronized int available() {
            return current.length - position;
        }

        @Override
        public void close() {
            ended = true;
        }
    }
}
