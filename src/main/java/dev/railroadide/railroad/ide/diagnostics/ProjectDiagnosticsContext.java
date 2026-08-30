package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.index.JavaAnalysisContextProvider;
import dev.railroadide.railroad.ide.language.index.DefaultProjectIndexContextResolver;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Reusable state for a project diagnostics export run.
 */
public record ProjectDiagnosticsContext(
    Project project,
    ProjectIndexContext indexContext,
    @Nullable JavaSymbolIndex javaSymbolIndex) {
    public ProjectDiagnosticsContext {
        project = Objects.requireNonNull(project, "project");
        indexContext = Objects.requireNonNull(indexContext, "indexContext");
    }

    public static ProjectDiagnosticsContext create(Project project) {
        Objects.requireNonNull(project, "project");

        ProjectIndexContext indexContext = new DefaultProjectIndexContextResolver().resolve(project);
        Railroad.LOGGER.info("Loading Java project index for diagnostics export from {}", indexContext.projectRoot());
        Services.PROJECT_LANGUAGE_INDEX_SERVICE.index(indexContext, JavaLanguageSupport.LANGUAGE_ID);
        JavaAnalysisContextProvider analysisContextProvider = JavaLanguageSupport.analysisContextProvider();
        JavaSymbolIndex javaSymbolIndex = analysisContextProvider.index(indexContext);
        return new ProjectDiagnosticsContext(project, indexContext, javaSymbolIndex);
    }
}
