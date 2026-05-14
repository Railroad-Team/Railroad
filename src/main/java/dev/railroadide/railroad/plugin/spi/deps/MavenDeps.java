package dev.railroadide.railroad.plugin.spi.deps;

import java.util.List;

/**
 * Represents the dependencies of a plugin, including Maven repositories and artifacts.
 * This class encapsulates the repositories and artifacts required for the plugin to function.
 */
public record MavenDeps(List<MavenRepo> repositories, List<MavenDep> artifacts) {
    /**
     * Constructs a MavenDeps object with the specified repositories and artifacts.
     *
     * @param repositories the list of Maven repositories
     * @param artifacts    the list of Maven dependencies
     */
    public MavenDeps {
        if (repositories == null || artifacts == null)
            throw new IllegalArgumentException("Repositories and artifacts cannot be null");
    }
}
