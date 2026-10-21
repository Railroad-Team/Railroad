package dev.railroadide.railroad.vcs.git.util;

import lombok.Getter;

/**
 * Pull strategy values exposed by the application.
 */
@Getter
public enum GitPullStrategy {
    /** Integrates fetched changes by merging, fast-forwarding where possible. */
    MERGE("railroad.git.pull.strategy.merge"),
    /** Replays local commits on top of the fetched upstream history. */
    REBASE("railroad.git.pull.strategy.rebase"),
    /** Accepts fetched changes only when the local branch can be fast-forwarded. */
    FAST_FORWARD_ONLY(
        "railroad.git.pull.strategy.fast_forward_only");

    /**
     * Translation key for the pull strategy's display label.
     *
     * @return the strategy label's localization key
     */
    private final String localizationKey;

    GitPullStrategy(String localizationKey) {
        this.localizationKey = localizationKey;
    }
}
