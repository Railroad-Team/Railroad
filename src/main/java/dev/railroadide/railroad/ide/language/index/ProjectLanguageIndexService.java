package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Lifecycle service for language-specific project indexes and their optional persisted caches.
 */
public final class ProjectLanguageIndexService {
    private final Map<String, ProjectLanguageIndexer<?, ?>> indexersByLanguageId = new ConcurrentHashMap<>();
    private final Object ownership = new Object();
    private final Map<Project, Session> sessions = new IdentityHashMap<>();
    private final Map<Path, Session> roots = new HashMap<>();
    private final Map<String, ProjectLanguageIndexPersistence<?>> persistenceByLanguageId = new ConcurrentHashMap<>();

    /** Identity of one open project, distinct from its reusable filesystem path. */
    public static final class Session {
        private final String id = java.util.UUID.randomUUID().toString();
        private final Path root;
        private final Map<String, Entry> entries = new HashMap<>();
        private volatile boolean closed;
        private volatile long configurationGeneration;
        private Session(Path root) { this.root = root; }
        /** Whether this session has permanently stopped accepting work. */
        public boolean isClosed() { return closed; }
        /** Revision of the live project configuration accepted for indexing. */
        public long configurationGeneration() { return configurationGeneration; }
        /** Opaque token carried by captured configurations, unrelated to the project path. */
        public String id() { return id; }
    }

    private static final class Entry {
        final ReentrantLock operation = new ReentrantLock();
        long generation;
        ProjectLanguageIndex<?> current;
    }

    /** Opens index ownership explicitly; repeated opens of the same active project reuse it. */
    public Session openProject(Project project) {
        synchronized (ownership) {
            Session existing = sessions.get(project);
            if (existing != null && !existing.closed) return existing;
            Path root = normalize(project.getPath());
            Session old = roots.get(root);
            if (old != null) closeProject(old);
            Session session = new Session(root);
            sessions.put(project, session);
            roots.put(root, session);
            return session;
        }
    }

    /** Returns existing ownership without opening a closed project. */
    public @Nullable Session session(Project project) {
        synchronized (ownership) { return sessions.get(project); }
    }

    /** Rejects old live contexts when the project adopts refreshed configuration. */
    public void refreshProject(Session session) {
        synchronized (ownership) {
            if (session == null || session.closed || roots.get(session.root) != session) return;
            session.configurationGeneration++;
            session.entries.values().forEach(entry -> { entry.generation++; entry.current = null; });
        }
    }

    /** Rejects late work and releases caches without waiting for an operation lock. */
    public void closeProject(Session session) {
        if (session == null) return;
        synchronized (ownership) {
            session.closed = true;
            session.entries.values().forEach(entry -> { entry.generation++; entry.current = null; });
            session.entries.clear();
            roots.remove(session.root, session);
            sessions.values().removeIf(value -> value == session);
        }
    }

    private ProjectIndexContext resolve(Project project) {
        Session session = session(project);
        long configuration = session == null ? 0 : session.configurationGeneration();
        ProjectIndexContext context = new DefaultProjectIndexContextResolver().resolve(project);
        return new ProjectIndexContext(project, context.projectRoot(), context.languages(), session, configuration);
    }

    private Entry entry(ProjectIndexContext context, String language) {
        synchronized (ownership) {
            check(context);
            return context.session().entries.computeIfAbsent(language, _ -> new Entry());
        }
    }

    private void check(ProjectIndexContext context) {
        Session session = context.session();
        if (session == null || session.closed || roots.get(context.projectRoot()) != session
            || session.configurationGeneration != context.configurationGeneration())
            throw new CancellationException("Project index session is no longer active");
    }

    private long generation(ProjectIndexContext context, Entry entry) {
        synchronized (ownership) { check(context); return entry.generation; }
    }

    private void publish(ProjectIndexContext context, Entry entry, long generation, ProjectLanguageIndex<?> index) {
        synchronized (ownership) {
            check(context);
            if (entry.generation != generation) throw new CancellationException("Index invalidated");
            entry.current = index;
        }
    }

    private static void lock(Entry entry) {
        try { entry.operation.lockInterruptibly(); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted waiting for index");
        }
    }

    /**
     * Registers an indexer for its language, rejecting duplicate registrations.
     *
     * @param indexer the indexer to register
     * @throws IllegalArgumentException if an adapter for the language is already registered
     */
    public void registerIndexer(ProjectLanguageIndexer<?, ?> indexer) {
        Objects.requireNonNull(indexer, "indexer");
        ProjectLanguageIndexer<?, ?> existing = indexersByLanguageId.putIfAbsent(indexer.languageId(), indexer);
        if (existing != null)
            throw new IllegalArgumentException(
                "Project language indexer for '" + indexer.languageId() + "' is already registered.");
    }

    /**
     * Registers a persistence adapter for its language, rejecting duplicate registrations.
     *
     * @param persistence the persistence adapter to register
     * @throws IllegalArgumentException if an adapter for the language is already registered
     */
    public void registerPersistence(ProjectLanguageIndexPersistence<?> persistence) {
        Objects.requireNonNull(persistence, "persistence");
        ProjectLanguageIndexPersistence<?> existing = persistenceByLanguageId.putIfAbsent(persistence.languageId(),
            persistence);
        if (existing != null)
            throw new IllegalArgumentException(
                "Project language index persistence for '" + persistence.languageId() + "' is already registered.");
    }

    /**
     * Checks whether a project indexer is registered for a language.
     *
     * @param languageId the stable language identifier
     * @return {@code true} if an indexer is registered
     */
    public boolean hasIndexer(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return indexersByLanguageId.containsKey(languageId);
    }

    /**
     * Checks whether an index persistence adapter is registered for a language.
     *
     * @param languageId the stable language identifier
     * @return {@code true} if a persistence adapter is registered
     */
    public boolean hasPersistence(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return persistenceByLanguageId.containsKey(languageId);
    }

    /**
     * Returns the registered project indexer for a language.
     *
     * @param languageId the stable language identifier
     * @return the indexer, or {@code null} if absent
     */
    public @Nullable ProjectLanguageIndexer<?, ?> getIndexer(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return indexersByLanguageId.get(languageId);
    }

    /**
     * Returns the registered index persistence adapter for a language.
     *
     * @param languageId the stable language identifier
     * @return the persistence adapter, or {@code null} if absent
     */
    public @Nullable ProjectLanguageIndexPersistence<?> getPersistence(String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return persistenceByLanguageId.get(languageId);
    }

    /**
     * Returns a project index already held in memory without building or loading one.
     *
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     * @return the current index, or {@code null} if unavailable
     */
    public @Nullable ProjectLanguageIndex<?> current(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        synchronized (ownership) {
            Session session = sessions.get(project);
            Entry entry = session == null ? null : session.entries.get(languageId);
            return entry == null ? null : entry.current;
        }
    }

    /**
     * Returns a project index already held in memory without building or loading one.
     *
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @return the current index, or {@code null} if unavailable
     */
    public @Nullable ProjectLanguageIndex<?> current(ProjectIndexContext context, String languageId) {
        Objects.requireNonNull(context, "context");
        if (context.language(languageId) == null)
            return null;

        synchronized (ownership) {
            check(context);
            Entry entry = context.session().entries.get(languageId);
            return entry == null ? null : entry.current;
        }
    }

    /**
     * Returns a project index already held in memory without building or loading one. The caller must supply
     * type parameters matching the language's registered indexer.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     * @return the current index, or {@code null} if unavailable
     */
    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I currentTyped(
        Project project,
        String languageId
    ) {
        return (I) current(project, languageId);
    }

    /**
     * Returns a project index already held in memory without building or loading one. The caller must supply
     * type parameters matching the language's registered indexer.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @return the current index, or {@code null} if unavailable
     */
    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I currentTyped(
        ProjectIndexContext context,
        String languageId
    ) {
        return (I) current(context, languageId);
    }

    /**
     * Returns an in-memory index or loads a current persisted index, building and saving one when necessary.
     *
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     * @return the project index, or {@code null} if the language context or indexer is unavailable
     */
    public @Nullable ProjectLanguageIndex<?> index(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return index(resolve(project), languageId);
    }

    /**
     * Returns an in-memory index or loads a current persisted index, building and saving one when necessary.
     *
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @return the project index, or {@code null} if the language context or indexer is unavailable
     */
    public @Nullable ProjectLanguageIndex<?> index(ProjectIndexContext context, String languageId) {
        Objects.requireNonNull(context, "context");
        return index(context, context.projectRoot(), languageId);
    }

    private @Nullable ProjectLanguageIndex<?> index(ProjectIndexContext context, Path projectRoot, String languageId) {
        LanguageIndexContext languageContext = context.language(languageId);
        if (languageContext == null)
            return null;

        Entry entry = entry(context, languageId);
        lock(entry);
        try {
            long generation = generation(context, entry);
            ProjectLanguageIndex<?> current = current(context, languageId);
            if (current != null) return current;
            ProjectLanguageIndexer<?, ?> indexer = indexersByLanguageId.get(languageId);
            if (indexer == null) return null;
            Collection<Path> files = collectFiles(languageContext).stream().filter(indexer::supports).toList();
            ProjectLanguageKey key = key(projectRoot, languageId);
            ProjectLanguageIndex<?> result = loadPersisted(key, files);
            boolean built = result == null;
            if (built) result = buildUnchecked(indexer, context, files);
            publish(context, entry, generation, result);
            if (built) savePersisted(context, entry, generation, key, result);
            return result;
        } finally { entry.operation.unlock(); }
    }

    /**
     * Returns an in-memory index or loads a current persisted index, building and saving one when necessary. The
     * caller must supply type parameters matching the language's registered indexer.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     * @return the project index, or {@code null} if the language context or indexer is unavailable
     */
    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I indexTyped(
        Project project,
        String languageId
    ) {
        return (I) index(project, languageId);
    }

    /**
     * Returns an in-memory index or loads a current persisted index, building and saving one when necessary. The
     * caller must supply type parameters matching the language's registered indexer.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @return the project index, or {@code null} if the language context or indexer is unavailable
     */
    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I indexTyped(
        ProjectIndexContext context,
        String languageId
    ) {
        return (I) index(context, languageId);
    }

    /**
     * Rebuilds a project index from source files and replaces its persisted and in-memory copies.
     *
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     * @return the rebuilt index, or {@code null} if the language context or indexer is unavailable
     */
    public @Nullable ProjectLanguageIndex<?> rebuild(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        return rebuild(resolve(project), languageId);
    }

    /**
     * Rebuilds a project index from source files and replaces its persisted and in-memory copies.
     *
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @return the rebuilt index, or {@code null} if the language context or indexer is unavailable
     */
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

        synchronized (ownership) {
            check(context);
            Entry existing = context.session().entries.get(languageId);
            if (existing != null) { existing.generation++; existing.current = null; }
        }
        Entry entry = entry(context, languageId);
        lock(entry);
        try {
            long generation = generation(context, entry);
            ProjectLanguageIndex<?> rebuilt = buildUnchecked(rawIndexer, context,
                collectFiles(languageContext).stream().filter(rawIndexer::supports).toList());
            publish(context, entry, generation, rebuilt);
            savePersisted(context, entry, generation, key(projectRoot, languageId), rebuilt);
            return rebuilt;
        } finally { entry.operation.unlock(); }
    }

    /**
     * Rebuilds a project index from source files and replaces its persisted and in-memory copies. The caller
     * must supply type parameters matching the language's registered indexer.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     * @return the rebuilt index, or {@code null} if the language context or indexer is unavailable
     */
    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I rebuildTyped(
        Project project,
        String languageId
    ) {
        return (I) rebuild(project, languageId);
    }

    /**
     * Rebuilds a project index from source files and replaces its persisted and in-memory copies. The caller
     * must supply type parameters matching the language's registered indexer.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @return the rebuilt index, or {@code null} if the language context or indexer is unavailable
     */
    @SuppressWarnings("unchecked")
    public <I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> @Nullable I rebuildTyped(
        ProjectIndexContext context,
        String languageId
    ) {
        return (I) rebuild(context, languageId);
    }

    /**
     * Reads and reindexes one file, replacing its entry in memory and persistence.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @param file the source file to process
     * @return the updated file index, or {@code null} when the file or language cannot be indexed
     */
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

        Entry entry = entry(context, languageId);
        lock(entry);
        try {
            long generation = generation(context, entry);
            I current = indexTyped(context, languageId);
            if (current == null) return null;
            F indexedFile = indexer.indexFile(context, normalizedFile, readSource(normalizedFile));
            I updated = indexer.withUpdatedFile(current, normalizedFile, indexedFile);
            publish(context, entry, generation, updated);
            savePersisted(context, entry, generation, key(normalizedRoot, languageId), updated);
            return indexedFile;
        } finally { entry.operation.unlock(); }
    }

    /**
     * Removes one file from the project index and updates persistence when indexing is available.
     *
     * @param <I> the project index type
     * @param <F> the per-file index type
     * @param context the project indexing context
     * @param languageId the stable language identifier
     * @param file the source file to process
     */
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

        Entry entry = entry(context, languageId);
        lock(entry);
        try {
            long generation = generation(context, entry);
            I current = indexTyped(context, languageId);
            if (current == null) return;
            I updated = indexer.withRemovedFile(current, normalizedFile);
            publish(context, entry, generation, updated);
            savePersisted(context, entry, generation, key(normalizedRoot, languageId), updated);
        } finally { entry.operation.unlock(); }
    }

    /**
     * Evicts the in-memory index for one project and language.
     *
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     */
    public void invalidate(Project project, String languageId) {
        Objects.requireNonNull(project, "project");
        synchronized (ownership) {
            Session session = sessions.get(project);
            if (session == null) return;
            Entry entry = session.entries.get(languageId);
            if (entry != null) { entry.generation++; entry.current = null; }
        }
    }

    /**
     * Deletes a project language's persisted index through its registered persistence adapter.
     *
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     */
    public void deletePersisted(Project project, String languageId) {
        Session session;
        Entry entry;
        long generation;
        synchronized (ownership) {
            session = sessions.get(project);
            if (session == null) return;
            entry = session.entries.computeIfAbsent(languageId, _ -> new Entry());
            generation = ++entry.generation;
            entry.current = null;
        }
        ProjectLanguageIndexPersistence<?> persistence = persistenceByLanguageId.get(languageId);
        if (persistence != null) persistence.delete(session.root, replacement -> {
            synchronized (ownership) {
                if (!session.closed && roots.get(session.root) == session
                    && entry.generation == generation && entry.current == null) replacement.run();
            }
        });
    }

    /**
     * Evicts all in-memory language indexes for a project.
     *
     * @param project the project whose files and configuration are used
     */
    public void invalidateProject(Project project) {
        synchronized (ownership) {
            Session session = sessions.get(project);
            if (session != null) session.entries.values().forEach(entry -> {
                entry.generation++;
                entry.current = null;
            });
        }
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
    private void savePersisted(ProjectIndexContext context, Entry entry, long generation,
                               ProjectLanguageKey key, ProjectLanguageIndex<?> index) {
        var persistence = (ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>>) persistenceByLanguageId.get(key.languageId());
        if (persistence == null) return;
        try {
            persistence.save(key.projectRoot(), index, replacement -> {
                synchronized (ownership) {
                    check(context);
                    if (entry.generation != generation || entry.current != index)
                        throw new CancellationException("Index invalidated before persistence commit");
                    replacement.run();
                }
            });
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(ProjectLanguageIndexService.class)
                .warn("Failed to persist index cache for {}", key.projectRoot(), exception);
        }
    }

    private record ProjectLanguageKey(Path projectRoot, String languageId) {
        private ProjectLanguageKey {
            Objects.requireNonNull(projectRoot, "projectRoot");
            Objects.requireNonNull(languageId, "languageId");
        }
    }
}
