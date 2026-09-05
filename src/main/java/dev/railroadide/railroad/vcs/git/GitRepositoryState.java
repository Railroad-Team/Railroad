package dev.railroadide.railroad.vcs.git;

/** Current state of repository detection for a project. */
public enum GitRepositoryState {
    /** Repository detection has not yet completed. */
    DETECTING,
    /** A Git repository has been found and is available for operations. */
    AVAILABLE,
    /** Detection completed without finding a Git repository for the project. */
    UNAVAILABLE,
    /** An error prevented repository detection from completing successfully. */
    FAILED
}
