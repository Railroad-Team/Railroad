package dev.railroadide.railroad.vcs.git.util;

/**
 * Result classification for cherry-pick operations.
 */
public enum CherryPickResult {
    /** The requested commit was cherry-picked successfully. */
    SUCCESS,
    /** The cherry-pick stopped with conflicts requiring resolution. */
    CONFLICTS,
    /** The operation failed without being classified as a conflict, or no repository was available. */
    FAILED
}
