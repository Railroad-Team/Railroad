package dev.railroadide.railroad.vcs.git.util;

import lombok.Getter;

/**
 * Push strategy values supported by git.
 */
@Getter
public enum GitPushStrategy {
    SIMPLE("railroad.git.push.strategy.simple"),
    CURRENT("railroad.git.push.strategy.current"),
    UPSTREAM("railroad.git.push.strategy.upstream"),
    MATCHING("railroad.git.push.strategy.matching"),
    NOTHING("railroad.git.push.strategy.nothing");

    private final String localizationKey;

    GitPushStrategy(String localizationKey) {
        this.localizationKey = localizationKey;
    }
}
