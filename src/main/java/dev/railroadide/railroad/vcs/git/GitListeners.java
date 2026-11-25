package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressEvent;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressListener;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Helpers for composing git output listeners.
 */
public final class GitListeners {
    private GitListeners() {
    }

    /**
     * Forwards Git's per-phase counts or percentages to one running activity.
     */
    public static Consumer<GitProgressEvent> activity(ActivityProgress progress) {
        return new Consumer<>() {
            private String phase = "";
            private long total = -1;
            private long done;
            private ActivityProgress.Unit unit;
            private LongConsumer counter;

            @Override
            public synchronized void accept(GitProgressEvent event) {
                switch (event) {
                    case GitProgressEvent.Percentage(String name, int percent, long completed, long count) -> {
                        ActivityProgress.Unit nextUnit = count > 0
                            ? ActivityProgress.Unit.COUNT
                            : ActivityProgress.Unit.PERCENT;
                        long nextDone = count > 0 ? completed : percent;
                        long nextTotal = count > 0 ? count : 100;
                        if (!phase.equals(name) || unit != nextUnit || total != nextTotal || nextDone < done) {
                            phase = name;
                            unit = nextUnit;
                            total = nextTotal;
                            counter = progress.begin(name, unit, nextDone, total);
                        } else {
                            counter.accept(nextDone);
                        }

                        done = nextDone;
                    }

                    case GitProgressEvent.Phase(String name) -> {
                        phase = name;
                        unit = null;
                        progress.indeterminate(name);
                    }

                    case GitProgressEvent.Message(String message) -> {
                        GitLog.LOGGER.debug("Git progress: {}", message);
                        if (unit != null && done == total) {
                            unit = null;
                            progress.indeterminate("");
                        }
                    }
                }
            }
        };
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
