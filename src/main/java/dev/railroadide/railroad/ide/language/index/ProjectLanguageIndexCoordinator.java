package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.utility.FileUtils;

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
    private final Path projectRoot;
    private final ProjectLanguageIndexService indexService;
    private final List<LanguageSupport> supports;

    public ProjectLanguageIndexCoordinator(Path projectRoot) {
        this(projectRoot, Services.PROJECT_LANGUAGE_INDEX_SERVICE, LanguageSupportRegistry.all());
    }

    public ProjectLanguageIndexCoordinator(
        Path projectRoot,
        ProjectLanguageIndexService indexService,
        Collection<LanguageSupport> supports
    ) {
        this.projectRoot = FileUtils.normalizePath(Objects.requireNonNull(projectRoot, "projectRoot"));
        this.indexService = Objects.requireNonNull(indexService, "indexService");
        this.supports = List.copyOf(Objects.requireNonNull(supports, "supports"));
    }

    public void warmIndexes() {
        for (LanguageSupport support : supports) {
            if (support.createIndexer() == null)
                continue;

            try {
                indexService.index(projectRoot, support.languageId());
            } catch (RuntimeException exception) {
                Railroad.LOGGER.warn(
                    "Failed to warm project index for language {} in {}",
                    support.languageId(),
                    projectRoot,
                    exception
                );
            }
        }
    }

    public void handleFileChange(Path path, WatchEvent.Kind<?> kind) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");

        LanguageSupport support = findSupport(path);
        if (support == null || support.createIndexer() == null)
            return;

        try {
            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                indexService.removeFile(projectRoot, support.languageId(), path);
                return;
            }

            if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                if (Files.isDirectory(path))
                    return;

                indexService.updateFile(projectRoot, support.languageId(), path);
            }
        } catch (RuntimeException exception) {
            Railroad.LOGGER.warn(
                "Failed to update project index for language {} and path {}",
                support.languageId(),
                path,
                exception
            );
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
