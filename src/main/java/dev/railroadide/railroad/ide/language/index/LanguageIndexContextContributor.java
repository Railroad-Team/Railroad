package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;

/**
 * Resolves indexing inputs for a particular language within a project.
 */
public interface LanguageIndexContextContributor {
    /**
     * Returns the stable identifier used to associate features and indexes with a language.
     *
     * @return the language identifier
     */
    String languageId();

    /**
     * Resolves indexing inputs from project configuration.
     *
     * @param project the project whose files and configuration are used
     * @return the resolved indexing context
     */
    LanguageIndexContext resolve(Project project);
}
