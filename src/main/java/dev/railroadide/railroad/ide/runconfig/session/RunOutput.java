package dev.railroadide.railroad.ide.runconfig.session;

import java.util.Objects;

/**
 * One chunk of text recorded by a run session. Chunks are not aligned to lines and may contain ANSI escape
 * sequences or split them across consecutive chunks of the same kind.
 *
 * @param kind where the text came from
 * @param text the recorded text
 */
public record RunOutput(Kind kind, String text) {
    /**
     * Validates the chunk.
     *
     * @param kind where the text came from
     * @param text the recorded text
     */
    public RunOutput {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    /**
     * Origin of recorded text.
     */
    public enum Kind {
        /**
         * Standard output of the launched process or build.
         */
        STDOUT,
        /**
         * Standard error of the launched process or build.
         */
        STDERR,
        /**
         * Messages written by Railroad itself, such as the launched command line.
         */
        SYSTEM,
        /**
         * Text the user sent to the process's standard input.
         */
        INPUT
    }
}
