package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageContribution;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import java.time.Duration;
import java.util.function.Consumer;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.ProjectConfigurationChangedEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Applies project-wide language index lifecycle updates in response to file system events.
 */
public final class ProjectLanguageIndexCoordinator {
    private volatile ProjectIndexContext context;
    private final ProjectLanguageIndexService indexService;
    private final ProviderExecutor providers;
    private final Duration providerBudget;
    private long languageRegistrationRevision = -1;

    /**
     * Creates a coordinator for warming indexes and applying project file events.
     *
     * @param project the project whose files and configuration are used
     */
    public ProjectLanguageIndexCoordinator(Project project) {
        this(project, Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(project));
    }

    /**
     * Creates a coordinator retaining the session captured before its worker was submitted.
     *
     * @param project the project whose files and configuration are used
     * @param session captured index ownership, or {@code null} when no live session exists
     */
    public ProjectLanguageIndexCoordinator(Project project, ProjectLanguageIndexService.Session session) {
        this(new DefaultProjectIndexContextResolver(_ -> session).resolve(project),
            Services.PROJECT_LANGUAGE_INDEX_SERVICE);
    }

    /**
     * Creates a coordinator for warming indexes and applying project file events.
     *
     * @param context the project indexing context
     * @param indexService the service managing project indexes
     */
    public ProjectLanguageIndexCoordinator(
        ProjectIndexContext context,
        ProjectLanguageIndexService indexService
    ) {
        this(context, indexService, ProviderExecutor.shared(), Duration.ofMinutes(2));
    }

    /**
     * Creates a coordinator with an explicitly owned provider execution boundary and budget.
     * The coordinator does not close that shared boundary.
     *
     * @param context project configuration and index session
     * @param indexService index publication owner
     * @param providers provider isolation and measurement boundary
     * @param providerBudget maximum duration of one language's source or auxiliary index work
     */
    public ProjectLanguageIndexCoordinator(
        ProjectIndexContext context,
        ProjectLanguageIndexService indexService,
        ProviderExecutor providers,
        Duration providerBudget
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.providerBudget = Objects.requireNonNull(providerBudget, "providerBudget");
        if (providerBudget.isNegative())
            throw new IllegalArgumentException("Provider budget must not be negative");
        this.indexService = Objects.requireNonNull(indexService, "indexService");
    }

    /**
     * Warms each supported project index and its auxiliary indexes, logging failures independently.
     */
    public void warmIndexes() {
        warmIndexes(null);
    }

    /**
     * Warms indexes using scheduler/provider cancellation and deadline controls.
     * Request controls are confined to this invocation and never retained for later file events.
     *
     * @param request captured execution controls, or null for synchronous session-owned work
     */
    public void warmIndexes(RequestContext request) {
        ProjectIndexContext operation = withRequest(refreshContext(), request);
        for (LanguageContribution support : indexService.languages()) {
            String languageId = indexService.indexLanguageId(operation.project(), support);
            operation.checkActive();
            if (!indexService.hasIndexer(operation.project(), languageId))
                continue;

            try {
                runProvider(support, request, "source", operation,
                    child -> indexService.index(child, languageId));
            } catch (CancellationException exception) {
                if (request == null)
                    throw exception;
                request.checkActive();
                operation.checkActive();
                continue;
            } catch (RuntimeException exception) {
                Railroad.LOGGER.warn(
                    "Failed to warm project index for language {} in {}",
                    languageId,
                    operation.projectRoot(),
                    exception);
            }

            try {
                runProvider(support, request, "auxiliary", operation,
                    child -> indexService.warmAdditionalIndexes(operation.project(), languageId, child));
            } catch (CancellationException exception) {
                if (request == null)
                    throw exception;
                request.checkActive();
                operation.checkActive();
            } catch (RuntimeException exception) {
                Railroad.LOGGER.warn(
                    "Failed to warm additional indexes for language {} in {}",
                    languageId,
                    operation.projectRoot(),
                    exception);
            }
        }
    }

    /**
     * Applies supported file creation, modification, and deletion events to the matching language index.
     *
     * @param path the file path to inspect
     * @param kind the file watcher event kind
     */
    public void handleFileChange(Path path, WatchEvent.Kind<?> kind) {
        handleFileChange(path, kind, null);
    }

    /**
     * Applies a file event with request controls checked by the index service before publication.
     *
     * @param path changed file
     * @param kind watcher event kind
     * @param request captured execution controls, or null for synchronous session-owned work
     */
    public void handleFileChange(Path path, WatchEvent.Kind<?> kind, RequestContext request) {
        ProjectIndexContext previous = context;
        if (previous.session() == null || previous.session().isClosed())
            return;
        ProjectIndexContext operation = withRequest(refreshContext(), request);
        operation.checkActive();
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        if (List
            .of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                "gradle.properties")
            .contains(path.getFileName().toString())) {
            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(context.project()));
        }

        boolean deleted = kind == StandardWatchEventKinds.ENTRY_DELETE;
        if (!deleted && kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_MODIFY)
            return;
        if (!deleted && Files.isDirectory(path))
            return;

        LanguageContribution winner = deleted ? null : indexService.findLanguage(path).orElse(null);
        for (LanguageContribution support : indexService.languages()) {
            String languageId = indexService.indexLanguageId(operation.project(), support);
            if (!indexService.hasIndexer(operation.project(), languageId))
                continue;
            boolean ownsFile = winner != null && winner.id().equals(support.id());
            ProjectLanguageIndex<?> current = indexService.current(operation, languageId);
            if (!ownsFile && (current == null || current.getFileIndex(path.toAbsolutePath().normalize()) == null))
                continue;
            try {
                runProvider(support, request, "source", operation, child -> {
                    if (ownsFile) {
                        indexService.updateFile(child, languageId, path);
                    } else {
                        indexService.removeFile(child, languageId, path);
                    }
                });
            } catch (CancellationException exception) {
                indexService.invalidate(operation.project(), languageId);
                if (request == null)
                    throw exception;
                request.checkActive();
                operation.checkActive();
            } catch (RuntimeException exception) {
                indexService.invalidate(operation.project(), languageId);
                Railroad.LOGGER.warn(
                    "Failed to update project index for language {} and path {}",
                    languageId, path, exception);
            }
        }
    }

    private void runProvider(
        LanguageContribution support,
        RequestContext request,
        String operation,
        ProjectIndexContext inputs,
        Consumer<ProjectIndexContext> work
    ) {
        if (request == null) {
            work.accept(inputs);
            return;
        }
        providers.executeWithContext("project-index:" + indexService.indexLanguageId(inputs.project(), support) + ":"
            + support.getClass().getName() + ":" + operation, request, providerBudget, child -> {
                work.accept(withRequest(inputs, child));
                return null;
            });
    }

    private synchronized ProjectIndexContext refreshContext() {
        long revision = indexService.languageRegistrationRevision();
        if (revision != languageRegistrationRevision
            || context.configurationGeneration() != context.session().configurationGeneration()) {
            context = indexService.refreshContext(context);
            languageRegistrationRevision = revision;
        }
        return context;
    }

    private static ProjectIndexContext withRequest(ProjectIndexContext context, RequestContext request) {
        if (request == null)
            return context;
        request.checkActive();
        return new ProjectIndexContext(context.project(), context.projectRoot(), context.languages(), context.session(),
            context.configurationGeneration(), () -> context.cancellationToken().isCancellationRequested()
                || request.cancellationToken().isCancellationRequested() || request.deadline().isExpired());
    }

}
