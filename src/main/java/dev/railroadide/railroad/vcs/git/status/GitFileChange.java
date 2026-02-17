package dev.railroadide.railroad.vcs.git.status;

import java.nio.file.Path;

/**
 * File-level change state extracted from git status output.
 *
 * @param path current path
 * @param oldPath previous path for rename/copy operations
 * @param indexStatus index status character
 * @param workTreeStatus working-tree status character
 */
public record GitFileChange(
    Path path,
    Path oldPath,
    char indexStatus,
    char workTreeStatus
) {
    /**
     * Creates a change without an explicit source path.
     *
     * @param path changed path
     * @param indexStatus index status character from porcelain output
     * @param workTreeStatus working-tree status character from porcelain output
     */
    public GitFileChange(Path path, char indexStatus, char workTreeStatus) {
        this(path, null, indexStatus, workTreeStatus);
    }

    /**
     * Indicates whether this change represents an untracked file.
     *
     * @return {@code true} when both index and work tree statuses are {@code '?'}
     */
    public boolean isUntracked() {
        return indexStatus == '?' && workTreeStatus == '?';
    }

    /**
     * Indicates whether this change is in conflict.
     *
     * @return {@code true} when either status is {@code 'U'}
     */
    public boolean isConflict() {
        return indexStatus == 'U' || workTreeStatus == 'U';
    }

    /**
     * Indicates whether this change includes a modification.
     *
     * @return {@code true} when either status is {@code 'M'}
     */
    public boolean isModified() {
        return indexStatus == 'M' || workTreeStatus == 'M';
    }

    /**
     * Indicates whether this change includes an added file.
     *
     * @return {@code true} when either status is {@code 'A'}
     */
    public boolean isAdded() {
        return indexStatus == 'A' || workTreeStatus == 'A';
    }

    /**
     * Indicates whether this change includes a deletion.
     *
     * @return {@code true} when either status is {@code 'D'}
     */
    public boolean isDeleted() {
        return indexStatus == 'D' || workTreeStatus == 'D';
    }

    /**
     * Indicates whether this change includes a rename.
     *
     * @return {@code true} when either status is {@code 'R'}
     */
    public boolean isRenamed() {
        return indexStatus == 'R' || workTreeStatus == 'R';
    }

    /**
     * Indicates whether this change includes a copy.
     *
     * @return {@code true} when either status is {@code 'C'}
     */
    public boolean isCopied() {
        return indexStatus == 'C' || workTreeStatus == 'C';
    }

    /**
     * Indicates whether this change has no differences.
     *
     * @return {@code true} when both status fields are spaces
     */
    public boolean isUnchanged() {
        return indexStatus == ' ' && workTreeStatus == ' ';
    }

    /**
     * Indicates whether the change is staged.
     *
     * @return {@code true} when index status is non-blank and not untracked
     */
    public boolean isStaged() {
        return indexStatus != ' ' && !isUntracked();
    }

    /**
     * Indicates whether the change is unstaged.
     *
     * @return {@code true} when working-tree status is non-blank and not untracked
     */
    public boolean isUnstaged() {
        return workTreeStatus != ' ' && !isUntracked();
    }
}
