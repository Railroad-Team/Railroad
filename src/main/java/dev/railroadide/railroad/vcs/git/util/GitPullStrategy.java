package dev.railroadide.railroad.vcs.git.util;

import lombok.Getter;

/**
 * Pull strategy values exposed by the application.
 */
@Getter
public enum GitPullStrategy {
    MERGE("railroad.git.pull.strategy.merge"),
    REBASE("railroad.git.pull.strategy.rebase"),
    FAST_FORWARD_ONLY("railroad.git.pull.strategy.fast_forward_only");

    private final String localizationKey;

    GitPullStrategy(String localizationKey) {
        this.localizationKey = localizationKey;
    }
}
