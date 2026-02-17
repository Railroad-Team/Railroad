package dev.railroadide.railroad.vcs.git.execution.progress;

/**
 * Output capture strategies used when reading git process streams.
 */
public enum GitResultCaptureMode {
    TEXT_LINES,
    NULL_RECORDS,
    TEXT_WHOLE
}
