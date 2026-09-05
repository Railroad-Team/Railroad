package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;

/**
 * Resolves a project's root and language-specific indexing contexts.
 */
public interface ProjectIndexContextResolver {
    /**
     * Resolves indexing inputs from project configuration.
     *
     * @param project the project whose files and configuration are used
     * @return the resolved indexing context
     */
    ProjectIndexContext resolve(Project project);
}
