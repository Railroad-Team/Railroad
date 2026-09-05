package dev.railroadide.railroad.vcs.git.diff;

/**
 * Diff scopes supported by git diff operations.
 */
public enum GitDiffMode {
    /** Compares the index with HEAD to show staged changes. */
    STAGED,
    /** Compares the working tree with the index to show unstaged changes. */
    UNSTAGED,
    /** Compares the working tree with HEAD to show staged and unstaged changes together. */
    HEAD
}
