package dev.railroadide.railroad.vcs.git.util;

/**
 * Supported values for branch or global rebase preferences.
 */
public enum GitRebaseSetting {
    /** Enables rebasing when integrating fetched changes. */
    REBASE,
    /** Disables rebasing so fetched changes are integrated by merging. */
    MERGE,
    /** Enables rebasing while recreating local merge commits. */
    MERGES,
    /** No rebase preference is configured at the queried scope. */
    UNSET;
}
