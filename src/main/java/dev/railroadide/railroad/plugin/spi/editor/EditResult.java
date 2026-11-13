package dev.railroadide.railroad.plugin.spi.editor;

/** Outcome of a checked selection or replacement. Rejected replacements do not mutate source text. */
public enum EditResult {
    /** The operation succeeded, including an empty or text-identical replacement. */
    APPLIED,
    /** The expected identity, revision, content or metadata is no longer current. */
    STALE,
    /** The request contains invalid bounds, overlaps, duplicate start offsets or null values. */
    INVALID,
    /** The target does not currently permit text replacement. */
    READ_ONLY,
    /** The editor's owning view has closed. */
    CLOSED,
    /** Published content is absent or has not been reconciled with the source buffer. */
    UNAVAILABLE
}
