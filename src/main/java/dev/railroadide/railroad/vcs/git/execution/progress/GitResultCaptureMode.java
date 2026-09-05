package dev.railroadide.railroad.vcs.git.execution.progress;

/**
 * Output capture strategies used when reading git process streams.
 */
public enum GitResultCaptureMode {
    /** Captures UTF-8 output as lines separated by carriage returns or line feeds, omitting empty lines. */
    TEXT_LINES,
    /** Captures UTF-8 output as records separated by NUL bytes, preserving newlines within records. */
    NULL_RECORDS,
    /** Captures nonempty UTF-8 output as one string, preserving its original line separators. */
    TEXT_WHOLE
}
