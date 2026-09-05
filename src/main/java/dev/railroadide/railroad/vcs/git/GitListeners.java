package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressEvent;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressListener;

import java.util.function.Consumer;

/**
 * Helpers for composing git output listeners.
 */
public final class GitListeners {
    private GitListeners() {
    }

    /**
     * Wraps a raw listener with progress parsing support.
     *
     * @param raw raw output listener
     * @param sink parsed progress event consumer
     * @param defaultPhase phase name used before parsing emits one
     * @return listener that forwards raw output and emits progress events
     */
    public static GitOutputListener withProgress(
        GitOutputListener raw,
        Consumer<GitProgressEvent> sink,
        String defaultPhase
    ) {
        return new GitProgressListener(raw, sink, defaultPhase);
    }
}
