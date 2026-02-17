package dev.railroadide.railroad.vcs.git.util;

/**
 * Result classification for cherry-pick operations.
 */
public enum CherryPickResult {
    SUCCESS,
    CONFLICTS,
    FAILED
}
