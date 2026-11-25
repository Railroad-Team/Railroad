package dev.railroadide.railroad.vcs.git.execution.progress;

/**
 * Marker hierarchy for parsed git progress updates.
 */
public sealed interface GitProgressEvent {
    /**
     * Free-form progress message text.
     *
     * @param text message text
     */
    record Message(String text) implements GitProgressEvent {
    }

    /**
     * Named progress phase update.
     *
     * @param name phase name
     */
    record Phase(String name) implements GitProgressEvent {
    }

    /**
     * Percentage progress update for a specific phase.
     *
     * @param phase phase name
     * @param completed completed items, or -1 when Git supplies only a percentage
     * @param total total items for the phase, or -1 when unknown
     * @param percent completion percentage
     */
    record Percentage(String phase, int percent, long completed, long total) implements GitProgressEvent {
    }
}
