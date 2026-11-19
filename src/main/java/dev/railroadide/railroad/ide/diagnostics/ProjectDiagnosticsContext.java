package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.index.DefaultProjectIndexContextResolver;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.util.Objects;

/**
 * Captured project roots and cancellation controls shared by a diagnostics export.
 *
 * @param project project whose diagnostics are being collected
 * @param indexContext captured indexing inputs and cancellation controls
 */
public record ProjectDiagnosticsContext(Project project, ProjectIndexContext indexContext) {
    /**
     * Creates a diagnostics context with non-null project and indexing inputs.
     *
     * @param project project whose diagnostics are being collected
     * @param indexContext captured indexing inputs and cancellation controls
     */
    public ProjectDiagnosticsContext {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(indexContext, "indexContext");
    }

    /**
     * Resolves language-neutral project inputs; each language session owns its analysis state.
     *
     * @param project project whose diagnostics are being collected
     * @return the resolved project diagnostics context
     */
    public static ProjectDiagnosticsContext create(Project project) {
        Objects.requireNonNull(project, "project");
        ProjectIndexContext indexContext = new DefaultProjectIndexContextResolver(
            Services.PROJECT_LANGUAGE_INDEX_SERVICE::session).resolve(project);
        return new ProjectDiagnosticsContext(project, indexContext);
    }
}
