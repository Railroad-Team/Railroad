package dev.railroadide.railroad.vcs.git.util;

import lombok.Getter;

/**
 * Push strategy values supported by git.
 */
@Getter
public enum GitPushStrategy {
    /** Pushes the current branch to the same name, requiring a matching upstream name in a central workflow. */
    SIMPLE("railroad.git.push.strategy.simple"),
    /** Pushes the current branch to a branch with the same name on the destination remote. */
    CURRENT("railroad.git.push.strategy.current"),
    /** Pushes the current branch back to its configured upstream branch. */
    UPSTREAM(
        "railroad.git.push.strategy.upstream"),
    /** Pushes local branches that have counterparts with the same names on the destination remote. */
    MATCHING(
        "railroad.git.push.strategy.matching"),
    /** Requires an explicit refspec instead of selecting branches to push by default. */
    NOTHING("railroad.git.push.strategy.nothing");

    /**
     * Translation key for the push strategy's display label.
     *
     * @return the strategy label's localization key
     */
    private final String localizationKey;

    GitPushStrategy(String localizationKey) {
        this.localizationKey = localizationKey;
    }
}
