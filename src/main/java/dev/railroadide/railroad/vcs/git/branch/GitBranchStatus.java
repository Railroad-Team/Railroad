package dev.railroadide.railroad.vcs.git.branch;

import lombok.Getter;

/**
 * Status classification used for branch health and sync state.
 */
@Getter
public enum GitBranchStatus {
    /** The branch has uncommitted changes or has diverged from its upstream. */
    DIRTY("railroad.git.branch.status.dirty"),
    /** The local branch is ahead of its upstream without being behind it. */
    LOCAL("railroad.git.branch.status.local"),
    /** The local branch is behind its upstream without being ahead of it. */
    REMOTE(
        "railroad.git.branch.status.remote"),
    /** No uncommitted changes or upstream differences were detected. */
    CLEAN("railroad.git.branch.status.clean");

    /**
     * Translation key for the status label shown in the branch browser.
     *
     * @return the status label's translation key
     */
    private final String translationKey;

    GitBranchStatus(String translationKey) {
        this.translationKey = translationKey;
    }
}
