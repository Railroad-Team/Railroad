package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;

/**
 * Creates a language feature for a file in a project.
 *
 * @param <T> the created feature type
 */
@FunctionalInterface
public interface LanguageFeatureFactory<T> {
    /**
     * Creates the feature for the supplied file and project context.
     *
     * @param project the project whose files and configuration are used
     * @param file the source file to process
     * @return the feature instance
     */
    T create(Project project, Path file);
}
