package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Lifecycle service for language-specific project indexes and their optional persisted caches.
 */
public final class ProjectLanguageIndexService {
    private final Map<String, ProjectLanguageIndexer<?, ?>> indexersByLanguageId = new ConcurrentHashMap<>();
    private final Map<ProjectLanguageKey, ProjectLanguageIndex<?>> indexesByProjectAndLanguage = new ConcurrentHashMap<>();
    private final Map<String, ProjectLanguageIndexPersistence<?>> persistenceByLanguageId = new ConcurrentHashMap<>();

    public void registerIndexer(ProjectLanguageIndexer<?, ?> indexer) {
        Objects.requireNonNull(indexer, "indexer");
        ProjectLanguageIndexer<?, ?> existing = indexersByLanguageId.putIfAbsent(indexer.languageId(), indexer);
        if (existing != null) {
            throw new IllegalArgumentException("Project language indexer for '" + indexer.languageId() + "' is already registered.");
        }
    }

    public void registerPersistence(ProjectLanguageIndexPersistence<?> persistence) {
        Objects.requireNonNull(persistence, "persistence");
        ProjectLanguageIndexPersistence<?> existing = persistenceByLanguageId.putIfAbsent(persistence.languageId(), persistence);
        if (existing != null) {
            throw new IllegalArgumentException("Project language index persistence for '" + persistence.languageId() + "' is already registered.");
        }
    }

    public boolean hasIndexer(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return indexersByLanguageId.containsKey(languageId);
    }

    public boolean hasPersistence(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return persistenceByLanguageId.containsKey(languageId);
    }

    public @Nullable ProjectLanguageIndexer<?, ?> getIndexer(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return indexersByLanguageId.get(languageId);
    }

    public @Nullable ProjectLanguageIndexPersistence<?> getPersistence(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return persistenceByLanguageId.get(languageId);
    }

    public @Nullable ProjectLanguageIndex<?> current(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return current(project.getPath(), languageId);
    }

    public @Nullable ProjectLanguageIndex<?> current(Path projectRoot, String languageId) {
        return indexesByProjectAndLanguage.get(key(projectRoot, languageId));
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I currentTyped(Path projectRoot, String languageId) {
        return (I) current(projectRoot, languageId);
    }

    public @Nullable ProjectLanguageIndex<?> index(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return index(project.getPath(), languageId);
    }

    public @Nullable ProjectLanguageIndex<?> index(Path projectRoot, String languageId) {
        ProjectLanguageKey key = key(projectRoot, languageId);
        ProjectLanguageIndex<?> current = indexesByProjectAndLanguage.get(key);
        if (current != null)
            return current;

        ProjectLanguageIndex<?> persisted = loadPersisted(key);
        if (persisted != null) {
            indexesByProjectAndLanguage.put(key, persisted);
            return persisted;
        }

        ProjectLanguageIndexer<?, ?> rawIndexer = indexersByLanguageId.get(languageId);
        if (rawIndexer == null)
            return null;

        ProjectLanguageIndex<?> built = buildUnchecked(rawIndexer, key.projectRoot());
        savePersisted(key, built);
        indexesByProjectAndLanguage.put(key, built);
        return built;
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I indexTyped(Path projectRoot, String languageId) {
        return (I) index(projectRoot, languageId);
    }

    public @Nullable ProjectLanguageIndex<?> rebuild(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return rebuild(project.getPath(), languageId);
    }

    public @Nullable ProjectLanguageIndex<?> rebuild(Path projectRoot, String languageId) {
        ProjectLanguageIndexer<?, ?> rawIndexer = indexersByLanguageId.get(languageId);
        if (rawIndexer == null)
            return null;

        Path normalizedRoot = normalize(projectRoot);
        ProjectLanguageKey key = new ProjectLanguageKey(normalizedRoot, languageId);
        ProjectLanguageIndex<?> rebuilt = buildUnchecked(rawIndexer, normalizedRoot);
        savePersisted(key, rebuilt);
        indexesByProjectAndLanguage.put(key, rebuilt);
        return rebuilt;
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I rebuildTyped(Path projectRoot, String languageId) {
        return (I) rebuild(projectRoot, languageId);
    }

    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable F updateFile(Path projectRoot, String languageId, Path file) {
        Path normalizedRoot = normalize(projectRoot);
        Path normalizedFile = normalize(file);

        @SuppressWarnings("unchecked")
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) indexersByLanguageId.get(languageId);
        if (indexer == null)
            return null;

        I current = indexTyped(normalizedRoot, languageId);
        if (current == null)
            return null;

        F indexedFile = indexer.indexFile(normalizedFile, readSource(normalizedFile));
        I updated = indexer.withUpdatedFile(current, normalizedFile, indexedFile);
        ProjectLanguageKey key = new ProjectLanguageKey(normalizedRoot, languageId);
        savePersisted(key, updated);
        indexesByProjectAndLanguage.put(key, updated);
        return indexedFile;
    }

    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> void removeFile(Path projectRoot, String languageId, Path file) {
        Path normalizedRoot = normalize(projectRoot);
        Path normalizedFile = normalize(file);

        @SuppressWarnings("unchecked")
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) indexersByLanguageId.get(languageId);
        if (indexer == null)
            return;

        I current = indexTyped(normalizedRoot, languageId);
        if (current == null)
            return;

        I updated = indexer.withRemovedFile(current, normalizedFile);
        ProjectLanguageKey key = new ProjectLanguageKey(normalizedRoot, languageId);
        savePersisted(key, updated);
        indexesByProjectAndLanguage.put(key, updated);
    }

    public void invalidate(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        invalidate(project.getPath(), languageId);
    }

    public void invalidate(Path projectRoot, String languageId) {
        indexesByProjectAndLanguage.remove(key(projectRoot, languageId));
    }

    public void deletePersisted(Path projectRoot, String languageId) {
        ProjectLanguageKey key = key(projectRoot, languageId);
        ProjectLanguageIndexPersistence<?> persistence = persistenceByLanguageId.get(key.languageId());
        if (persistence != null) {
            persistence.delete(key.projectRoot());
        }
    }

    public void invalidateProject(Path projectRoot) {
        Path normalizedRoot = normalize(projectRoot);
        indexesByProjectAndLanguage.keySet().removeIf(key -> key.projectRoot().equals(normalizedRoot));
    }

    private ProjectLanguageKey key(Path projectRoot, String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return new ProjectLanguageKey(normalize(projectRoot), languageId);
    }

    private static Path normalize(Path path) {
        return FileUtils.normalizePath(Objects.requireNonNull(path, "path"));
    }

    private static Collection<Path> collectFiles(Path projectRoot) {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .map(ProjectLanguageIndexService::normalize)
                .toList();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to scan project files for " + projectRoot, exception);
        }
    }

    private static String readSource(Path file) {
        try {
            return Files.readString(file);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read " + file, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> I buildUnchecked(ProjectLanguageIndexer<?, ?> rawIndexer, Path projectRoot) {
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) rawIndexer;
        return indexer.build(projectRoot, collectFiles(projectRoot));
    }

    private @Nullable ProjectLanguageIndex<?> loadPersisted(ProjectLanguageKey key) {
        ProjectLanguageIndexPersistence<?> persistence = persistenceByLanguageId.get(key.languageId());
        if (persistence == null)
            return null;

        return persistence.loadIfCurrent(key.projectRoot());
    }

    @SuppressWarnings("unchecked")
    private void savePersisted(ProjectLanguageKey key, ProjectLanguageIndex<?> index) {
        ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>> persistence =
            (ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>>) persistenceByLanguageId.get(key.languageId());
        if (persistence != null) {
            persistence.save(key.projectRoot(), index);
        }
    }

    private record ProjectLanguageKey(Path projectRoot, String languageId) {
        private ProjectLanguageKey {
            Objects.requireNonNull(projectRoot, "projectRoot");
            Objects.requireNonNull(languageId, "languageId");
        }
    }
}
