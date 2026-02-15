package dev.railroadide.railroad.vcs.git.util;

import lombok.Getter;

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
