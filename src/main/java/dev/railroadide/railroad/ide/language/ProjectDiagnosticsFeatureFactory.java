package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.diagnostics.ProjectDiagnosticsContext;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;

/**
 * Creates a file diagnostics feature using a shared project diagnostics context.
 *
 * @param <T> the created feature type
 */
@FunctionalInterface
public interface ProjectDiagnosticsFeatureFactory<T> {
    /**
     * Creates the feature for the supplied file and project context.
     *
     * @param context the shared project diagnostics context
     * @param file the source file to process
     * @return the feature instance
     */
    T create(ProjectDiagnosticsContext context, Path file);
}
