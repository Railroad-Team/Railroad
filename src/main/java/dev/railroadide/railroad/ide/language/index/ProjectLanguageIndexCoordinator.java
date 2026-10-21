package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Applies project-wide language index lifecycle updates in response to file system events.
 */
public final class ProjectLanguageIndexCoordinator {
    private final ProjectIndexContext context;
    private final ProjectLanguageIndexService indexService;
    private final List<LanguageSupport> supports;

    /**
     * Creates a coordinator for warming indexes and applying project file events.
     *
     * @param project the project whose files and configuration are used
     */
    public ProjectLanguageIndexCoordinator(Project project) {
        this(new DefaultProjectIndexContextResolver().resolve(project), Services.PROJECT_LANGUAGE_INDEX_SERVICE,
            LanguageSupportRegistry.all());
    }

    /**
     * Creates a coordinator for warming indexes and applying project file events.
     *
     * @param context the project indexing context
     * @param indexService the service managing project indexes
     * @param supports the language support implementations
     */
    public ProjectLanguageIndexCoordinator(
        ProjectIndexContext context,
        ProjectLanguageIndexService indexService,
        Collection<LanguageSupport> supports
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.indexService = Objects.requireNonNull(indexService, "indexService");
        this.supports = List.copyOf(Objects.requireNonNull(supports, "supports"));
    }

    /**
     * Warms each supported project index and its auxiliary indexes, logging failures independently.
     */
    public void warmIndexes() {
        for (LanguageSupport support : supports) {
            if (support.createIndexer() == null)
                continue;

            try {
                indexService.index(context, support.languageId());
            } catch (RuntimeException exception) {
                Railroad.LOGGER.warn(
                    "Failed to warm project index for language {} in {}",
                    support.languageId(),
                    context.projectRoot(),
                    exception);
            }

            try {
                support.warmAdditionalIndexes(context);
            } catch (RuntimeException exception) {
                Railroad.LOGGER.warn(
                    "Failed to warm additional indexes for language {} in {}",
                    support.languageId(),
                    context.projectRoot(),
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
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");

        LanguageSupport support = findSupport(path);
        if (support == null || support.createIndexer() == null)
            return;

        try {
            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                indexService.removeFile(context, support.languageId(), path);
                return;
            }

            if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                if (Files.isDirectory(path))
                    return;

                indexService.updateFile(context, support.languageId(), path);
            }
        } catch (RuntimeException exception) {
            Railroad.LOGGER.warn(
                "Failed to update project index for language {} and path {}",
                support.languageId(),
                path,
                exception);
        }
    }

    private LanguageSupport findSupport(Path path) {
        for (LanguageSupport support : supports) {
            if (support.supports(path))
                return support;
        }

        return null;
    }
}
