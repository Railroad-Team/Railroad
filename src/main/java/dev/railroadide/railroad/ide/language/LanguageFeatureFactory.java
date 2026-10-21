package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.plugin.spi.dto.Project;

/**
 * Creates language features for physical or virtual documents in a project.
 *
 * @param <T> the created feature type
 */
@FunctionalInterface
public interface LanguageFeatureFactory<T> {
    /**
     * Creates a feature for a physical or virtual document.
     *
     * @param project the project whose files and configuration are used
     * @param documentUri the physical or virtual document location
     * @return the feature instance
     */
    T create(Project project, DocumentUri documentUri);
}
