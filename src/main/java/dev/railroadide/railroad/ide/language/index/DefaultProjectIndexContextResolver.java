package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.LanguageRuntime;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.utility.FileUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Combines contexts from registered language contributors and normalizes the project root.
 */
public final class DefaultProjectIndexContextResolver implements ProjectIndexContextResolver {
    private final Function<Project, ProjectLanguageIndexService.Session> sessions;
    private final LanguageRuntime runtime;

    /** Creates a resolver for standalone contexts without live index ownership. */
    public DefaultProjectIndexContextResolver() {
        this(_ -> null);
    }

    /**
     * Creates a resolver bound to an index service's active sessions.
     *
     * @param sessions looks up existing project ownership, returning {@code null} for standalone contexts
     */
    public DefaultProjectIndexContextResolver(Function<Project, ProjectLanguageIndexService.Session> sessions) {
        this(sessions, LanguageSupportRegistry.runtime());
    }

    /**
     * Resolves only contributions owned by the supplied runtime.
     *
     * @param sessions looks up project index ownership, returning null for standalone contexts
     * @param runtime runtime supplying the registered language contributions
     */
    public DefaultProjectIndexContextResolver(
        Function<Project, ProjectLanguageIndexService.Session> sessions,
        LanguageRuntime runtime
    ) {
        this.sessions = Objects.requireNonNull(sessions);
        this.runtime = Objects.requireNonNull(runtime);
    }
    @Override
    public ProjectIndexContext resolve(Project project) {
        Objects.requireNonNull(project, "project");
        var session = sessions.apply(project);
        long generation = session == null ? 0 : session.configurationGeneration();
        if (project instanceof RailroadProject railroadProject) {
            railroadProject.awaitFacetDiscovery();
        }

        Map<String, LanguageIndexContext> contexts = runtime.all().stream()
            .filter(contribution -> contribution.supportedCapabilities().contains(LanguageCapabilities.INDEX_CONTEXT))
            .flatMap(contribution -> runtime.session(project, contribution.id(), "")
                .capability(LanguageCapabilities.INDEX_CONTEXT).stream())
            .map(contributor -> contributor.resolve(project))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(LanguageIndexContext::languageId, Function.identity()));

        return new ProjectIndexContext(
            project,
            FileUtils.normalizePath(project.getPath()),
            contexts, session, generation);
    }
}
