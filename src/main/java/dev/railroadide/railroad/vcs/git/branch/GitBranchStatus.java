package dev.railroadide.railroad.vcs.git.branch;

import lombok.Getter;

/**
 * Status classification used for branch health and sync state.
 */
@Getter
public enum GitBranchStatus {
    DIRTY("railroad.git.branch.status.dirty"),
    LOCAL("railroad.git.branch.status.local"),
    REMOTE("railroad.git.branch.status.remote"),
    CLEAN("railroad.git.branch.status.clean");

    private final String translationKey;

    GitBranchStatus(String translationKey) {
        this.translationKey = translationKey;
    }
}
