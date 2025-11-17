package dev.railroadide.railroad.java.cli;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for managing external {@link Process} instances, primarily for enforcing execution timeouts.
 */
public final class ProcessExecution {
    private ProcessExecution() {
    }

    /**
     * Waits for a process to complete, enforcing a specified timeout. If the process does not
     * finish within the given duration, it is forcibly terminated.
     *
     * @param process  The {@link Process} to monitor.
     * @param duration The maximum time to wait. If zero or negative, no timeout is enforced.
     * @param unit     The {@link TimeUnit} for the duration.
     * @param toolName The name of the tool being executed, used for logging and error messages.
     * @throws RuntimeException if the process times out or the waiting thread is interrupted.
     */
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

    /**
     * Forcefully terminates a process, trying a graceful destruction first, followed by a forcible one if it remains alive.
     *
     * @param process The process to destroy.
     */
    private static void destroyProcess(Process process) {
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
