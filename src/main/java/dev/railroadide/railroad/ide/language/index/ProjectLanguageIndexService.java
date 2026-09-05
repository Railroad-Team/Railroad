package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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
        if (existing != null)
            throw new IllegalArgumentException(
                "Project language indexer for '" + indexer.languageId() + "' is already registered.");
    }

    public void registerPersistence(ProjectLanguageIndexPersistence<?> persistence) {
        Objects.requireNonNull(persistence, "persistence");
        ProjectLanguageIndexPersistence<?> existing = persistenceByLanguageId.putIfAbsent(persistence.languageId(),
            persistence);
        if (existing != null)
            throw new IllegalArgumentException(
                "Project language index persistence for '" + persistence.languageId() + "' is already registered.");
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
        return current(new DefaultProjectIndexContextResolver().resolve(project), languageId);
    }

    public @Nullable ProjectLanguageIndex<?> current(ProjectIndexContext context, String languageId) {
        Objects.requireNonNull(context, "context");
        if (context.language(languageId) == null)
            return null;

        return indexesByProjectAndLanguage.get(key(context.projectRoot(), languageId));
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I currentTyped(
        Project project,
        String languageId
    ) {
        return (I) current(project, languageId);
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I currentTyped(
        ProjectIndexContext context,
        String languageId
    ) {
        return (I) current(context, languageId);
    }

    public @Nullable ProjectLanguageIndex<?> index(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return index(new DefaultProjectIndexContextResolver().resolve(project), languageId);
    }

    public @Nullable ProjectLanguageIndex<?> index(ProjectIndexContext context, String languageId) {
        Objects.requireNonNull(context, "context");
        return index(context, context.projectRoot(), languageId);
    }

    private @Nullable ProjectLanguageIndex<?> index(ProjectIndexContext context, Path projectRoot, String languageId) {
        LanguageIndexContext languageContext = context.language(languageId);
        if (languageContext == null)
            return null;

        ProjectLanguageKey key = key(projectRoot, languageId);
        ProjectLanguageIndex<?> current = indexesByProjectAndLanguage.get(key);
        if (current != null)
            return current;

        ProjectLanguageIndexer<?, ?> rawIndexer = indexersByLanguageId.get(languageId);
        if (rawIndexer == null)
            return null;

        Collection<Path> sourceFiles = collectFiles(languageContext).stream()
            .filter(rawIndexer::supports)
            .toList();
        ProjectLanguageIndex<?> persisted = loadPersisted(key, sourceFiles);
        if (persisted != null) {
            indexesByProjectAndLanguage.put(key, persisted);
            return persisted;
        }

        ProjectLanguageIndex<?> built = buildUnchecked(rawIndexer, context, sourceFiles);
        savePersisted(key, built);
        indexesByProjectAndLanguage.put(key, built);
        return built;
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I indexTyped(
        Project project,
        String languageId
    ) {
        return (I) index(project, languageId);
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I indexTyped(
        ProjectIndexContext context,
        String languageId
    ) {
        return (I) index(context, languageId);
    }

    public @Nullable ProjectLanguageIndex<?> rebuild(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return rebuild(new DefaultProjectIndexContextResolver().resolve(project), languageId);
    }

    public @Nullable ProjectLanguageIndex<?> rebuild(ProjectIndexContext context, String languageId) {
        Objects.requireNonNull(context, "context");
        return rebuild(context, context.projectRoot(), languageId);
    }

    private @Nullable ProjectLanguageIndex<?> rebuild(
        ProjectIndexContext context,
        Path projectRoot,
        String languageId
    ) {
        LanguageIndexContext languageContext = context.language(languageId);
        if (languageContext == null)
            return null;

        ProjectLanguageIndexer<?, ?> rawIndexer = indexersByLanguageId.get(languageId);
        if (rawIndexer == null)
            return null;

        var key = new ProjectLanguageKey(projectRoot, languageId);
        ProjectLanguageIndex<?> rebuilt = buildUnchecked(rawIndexer, context, collectFiles(languageContext));
        savePersisted(key, rebuilt);
        indexesByProjectAndLanguage.put(key, rebuilt);
        return rebuilt;
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I rebuildTyped(
        Project project,
        String languageId
    ) {
        return (I) rebuild(project, languageId);
    }

    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I rebuildTyped(
        ProjectIndexContext context,
        String languageId
    ) {
        return (I) rebuild(context, languageId);
    }

    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable F updateFile(
        ProjectIndexContext context,
        String languageId,
        Path file
    ) {
        Path normalizedRoot = normalize(context.projectRoot());
        Path normalizedFile = normalize(file);
        LanguageIndexContext languageContext = context.language(languageId);
        if (languageContext == null || !isIndexedFile(languageContext, normalizedFile))
            return null;

        @SuppressWarnings("unchecked")
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) indexersByLanguageId.get(languageId);
        if (indexer == null)
            return null;

        I current = indexTyped(context, languageId);
        if (current == null)
            return null;

        F indexedFile = indexer.indexFile(context, normalizedFile, readSource(normalizedFile));
        I updated = indexer.withUpdatedFile(current, normalizedFile, indexedFile);
        var key = new ProjectLanguageKey(normalizedRoot, languageId);
        updatePersistedFile(key, updated, normalizedFile);
        indexesByProjectAndLanguage.put(key, updated);
        return indexedFile;
    }

    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> void removeFile(
        ProjectIndexContext context,
        String languageId,
        Path file
    ) {
        Path normalizedRoot = normalize(context.projectRoot());
        Path normalizedFile = normalize(file);
        LanguageIndexContext languageContext = context.language(languageId);
        if (languageContext == null || !isIndexedFile(languageContext, normalizedFile))
            return;

        @SuppressWarnings("unchecked")
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) indexersByLanguageId.get(languageId);
        if (indexer == null)
            return;

        I current = indexTyped(context, languageId);
        if (current == null)
            return;

        I updated = indexer.withRemovedFile(current, normalizedFile);
        var key = new ProjectLanguageKey(normalizedRoot, languageId);
        removePersistedFile(key, updated, normalizedFile);
        indexesByProjectAndLanguage.put(key, updated);
    }

    public void invalidate(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        indexesByProjectAndLanguage.remove(key(project.getPath(), languageId));
    }

    public void deletePersisted(Project project, String languageId) {
        ProjectLanguageKey key = key(project.getPath(), languageId);
        ProjectLanguageIndexPersistence<?> persistence = persistenceByLanguageId.get(key.languageId());
        if (persistence != null) {
            persistence.delete(key.projectRoot());
        }
    }

    public void invalidateProject(Project project) {
        Path normalizedRoot = normalize(project.getPath());
        indexesByProjectAndLanguage.keySet().removeIf(key -> key.projectRoot().equals(normalizedRoot));
    }

    private ProjectLanguageKey key(Path projectRoot, String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return new ProjectLanguageKey(normalize(projectRoot), languageId);
    }

    private static Path normalize(Path path) {
        return FileUtils.normalizePath(Objects.requireNonNull(path, "path"));
    }

    private static Collection<Path> collectFiles(LanguageIndexContext languageContext) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        collectFiles(files, languageContext.sourceRoots());
        collectFiles(files, languageContext.generatedRoots());
        return files;
    }

    private static void collectFiles(LinkedHashSet<Path> files, Collection<Path> roots) {
        for (Path root : roots) {
            Path normalizedRoot = normalize(root);
            if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot))
                continue;

            try (Stream<Path> paths = Files.walk(normalizedRoot)) {
                paths
                    .filter(Files::isRegularFile)
                    .map(ProjectLanguageIndexService::normalize)
                    .forEach(files::add);
            } catch (Exception exception) {
                throw new RuntimeException("Failed to scan language files for " + normalizedRoot, exception);
            }
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
    private static <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> I buildUnchecked(
        ProjectLanguageIndexer<?, ?> rawIndexer,
        ProjectIndexContext context,
        Collection<Path> sourceFiles
    ) {
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) rawIndexer;
        return indexer.build(context, sourceFiles);
    }

    private static boolean isIndexedFile(LanguageIndexContext languageContext, Path file) {
        return isWithinRoots(file, languageContext.sourceRoots())
            || isWithinRoots(file, languageContext.generatedRoots());
    }

    private static boolean isWithinRoots(Path file, Collection<Path> roots) {
        for (Path root : roots) {
            if (file.startsWith(normalize(root)))
                return true;
        }

        return false;
    }

    private @Nullable ProjectLanguageIndex<?> loadPersisted(ProjectLanguageKey key, Collection<Path> sourceFiles) {
        ProjectLanguageIndexPersistence<?> persistence = persistenceByLanguageId.get(key.languageId());
        if (persistence == null)
            return null;

        return persistence.loadIfCurrent(key.projectRoot(), sourceFiles);
    }

    @SuppressWarnings("unchecked")
    private void savePersisted(ProjectLanguageKey key, ProjectLanguageIndex<?> index) {
        ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>> persistence = (ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>>) persistenceByLanguageId
            .get(key.languageId());
        if (persistence != null) {
            persistence.save(key.projectRoot(), index);
        }
    }

    @SuppressWarnings("unchecked")
    private void updatePersistedFile(ProjectLanguageKey key, ProjectLanguageIndex<?> index, Path file) {
        ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>> persistence = (ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>>) persistenceByLanguageId
            .get(key.languageId());
        if (persistence != null) {
            persistence.updateFile(key.projectRoot(), index, file);
        }
    }

    @SuppressWarnings("unchecked")
    private void removePersistedFile(ProjectLanguageKey key, ProjectLanguageIndex<?> index, Path file) {
        ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>> persistence = (ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>>) persistenceByLanguageId
            .get(key.languageId());
        if (persistence != null) {
            persistence.removeFile(key.projectRoot(), index, file);
        }
    }

    private record ProjectLanguageKey(Path projectRoot, String languageId) {
        private ProjectLanguageKey {
            Objects.requireNonNull(projectRoot, "projectRoot");
            Objects.requireNonNull(languageId, "languageId");
        }
    }
}
