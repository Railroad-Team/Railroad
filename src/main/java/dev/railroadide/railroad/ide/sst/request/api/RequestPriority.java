package dev.railroadide.railroad.ide.sst.request.api;

/**
 * Shared scheduling order for language work, from most to least interactive.
 * Priority orders queued requests; it does not preempt a running provider. Requests
 * with equal priority execute in submission order when a worker becomes available.
 */
public enum RequestPriority {
    /**
     * Highest priority for completion suggestions requested while editing.
     */
    COMPLETION,
    /** Priority for displaying callable signatures at the current argument position. */
    SIGNATURE_HELP,
    /** Priority for explicitly requested navigation to a symbol's definition. */
    NAVIGATION,
    /** Priority for diagnostics presented in the visible editor. */
    VISIBLE_DIAGNOSTICS,
    /** Priority for analysis of open documents below immediately visible feedback. */
    OPEN_DOCUMENT_ANALYSIS,
    /** Lowest priority for indexing workspace content in the background. */
    BACKGROUND_INDEXING
}
