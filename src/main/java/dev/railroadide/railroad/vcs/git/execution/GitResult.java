package dev.railroadide.railroad.vcs.git.execution;

import java.time.Duration;
import java.util.List;

/**
 * Captured result data from a completed git command.
 *
 * @param exitCode process exit code
 * @param stdout captured stdout entries
 * @param stderr captured stderr entries
 * @param timedOut whether process timed out
 * @param cancelled whether execution was cancelled
 * @param duration total execution duration
 */
public record GitResult(int exitCode, List<String> stdout, List<String> stderr, boolean timedOut, boolean cancelled, Duration duration) {
    /**
     * Returns the first stdout line, or an empty string.
     *
     * @return first stdout line, or {@code ""} when stdout is empty
     */
    public String readFirstStdoutLine() {
        if (stdout.isEmpty())
            return "";

        return stdout.getFirst();
    }

    /**
     * Returns the first stderr line, or an empty string.
     *
     * @return first stderr line, or {@code ""} when stderr is empty
     */
    public String readFirstStderrLine() {
        if (stderr.isEmpty())
            return "";

        return stderr.getFirst();
    }

    /**
     * Returns stdout joined by newline characters.
     *
     * @return complete stdout text with newline separators
     */
    public String readAllStdout() {
        return String.join("\n", stdout);
    }
}
