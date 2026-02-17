package dev.railroadide.railroad.vcs.git.execution.progress;

import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;

import java.util.function.Consumer;

/**
 * Decorates raw git output callbacks with parsed progress events.
 */
public final class GitProgressListener implements GitOutputListener {
    public static final GitProgressListener NO_OP = new GitProgressListener(GitOutputListener.NO_OP, $ -> {}, null);

    private final GitOutputListener raw;
    private final Consumer<GitProgressEvent> sink;

    private volatile String currentPhase;

    /**
     * Creates a listener that forwards raw output and emits parsed progress events.
     *
     * @param raw raw output listener to forward lines to
     * @param sink consumer receiving parsed progress events
     * @param initialPhase default phase name used before the first parsed phase
     */
    public GitProgressListener(GitOutputListener raw, Consumer<GitProgressEvent> sink, String initialPhase) {
        this.raw = raw;
        this.sink = sink;
        this.currentPhase = initialPhase == null ? "(working)" : initialPhase;
    }

    /**
     * Receives stdout text and attempts to emit progress events.
     *
     * @param line stdout line text
     */
    @Override
    public void onStdout(String line) {
        if (raw != null) {
            raw.onStdout(line);
        }

        emitIfProgress(line);
    }

    /**
     * Receives stderr text and attempts to emit progress events.
     *
     * @param line stderr line text
     */
    @Override
    public void onStderr(String line) {
        if (raw != null) {
            raw.onStderr(line);
        }

        emitIfProgress(line);
    }

    /**
     * Receives null-delimited stdout records and forwards them to the raw listener.
     *
     * @param record stdout record text
     */
    @Override
    public void onStdoutRecord(String record) {
        if (raw != null) {
            raw.onStdoutRecord(record);
        }
    }

    private void emitIfProgress(String line) {
        GitProgressParser.tryParse(line, currentPhase).ifPresent(event -> {
            if (event instanceof GitProgressEvent.Phase(String name)) {
                currentPhase = name;
            }

            // TODO: Replace 'ignored' with '_' in java 25
            if (event instanceof GitProgressEvent.Percentage(String phase, int ignored)) {
                currentPhase = phase;
            }

            if (sink != null) {
                sink.accept(event);
            }
        });
    }
}
