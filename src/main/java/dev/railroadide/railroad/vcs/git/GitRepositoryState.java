package dev.railroadide.railroad.vcs.git;

/** Current state of repository detection for a project. */
public enum GitRepositoryState {
    DETECTING,
    AVAILABLE,
    UNAVAILABLE,
    FAILED
}
