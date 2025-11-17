package dev.railroadide.railroad.java.cli;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ProcessExecution {
    private ProcessExecution() {
    }

    public static void enforceTimeout(Process process, long duration, TimeUnit unit, String toolName) {
        Objects.requireNonNull(process, "Process cannot be null");
        Objects.requireNonNull(toolName, "Tool name cannot be null");
        if (duration <= 0)
            return;

        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        boolean finished;
        try {
            finished = process.waitFor(duration, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            destroyProcess(process);
            throw new RuntimeException("Interrupted while waiting for " + toolName + " process to finish.", exception);
        }

        if (!finished) {
            destroyProcess(process);
            throw new RuntimeException(toolName + " process timed out after "
                + duration + " " + unit.toString().toLowerCase(Locale.ROOT));
        }
    }

    private static void destroyProcess(Process process) {
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
