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
import dev.railroadide.railroad.ide.language.LanguageRuntime;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageId;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageSession;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageContribution;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;

/**
 * Lifecycle service for language-specific project indexes and their optional persisted caches.
 */
public final class ProjectLanguageIndexService {
    private final LanguageRuntime runtime;
    private final Object ownership = new Object();
    private final Map<Project, Session> sessions = new IdentityHashMap<>();
    private final Map<Path, Session> roots = new HashMap<>();

    /** Creates index ownership backed by the application's language runtime. */
    public ProjectLanguageIndexService() {
        this(LanguageSupportRegistry.runtime());
    }

    /**
     * Creates index ownership backed by the supplied language runtime.
     *
     * @param runtime runtime owning the language contributions and their project sessions
     */
    public ProjectLanguageIndexService(LanguageRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    private LanguageSession languageSession(Project project, String languageId) {
        var id = new LanguageId(languageId.contains(":") ? languageId : "railroad:" + languageId);
        return runtime.get(id).isEmpty() ? null : runtime.session(project, id, "");
    }

    /** Identity of one open project, distinct from its reusable filesystem path. */
    public static final class Session {
        private final String id = UUID.randomUUID().toString();
        private final Path root;
        private final Map<String, Entry> entries = new HashMap<>();
        private volatile boolean closed;
        private volatile long configurationGeneration;
        private long registrationRevision = -1;
        private Session(Path root) {
            this.root = root;
        }
        /**
         * Reports whether this session has permanently stopped accepting work.
         *
         * @return {@code true} once this session has closed
         */
        public boolean isClosed() {
            return closed;
        }
        /**
         * Returns the revision of the live project configuration accepted for indexing.
         *
         * @return current configuration generation, initially zero
         */
        public long configurationGeneration() {
            return configurationGeneration;
        }
        /**
         * Returns an opaque token carried by captured configurations, unrelated to the project path.
         *
         * @return stable identifier unique to this open session
         */
        public String id() {
            return id;
        }
    }

    private static final class Entry {
        private final ReentrantLock operation = new ReentrantLock();
        private final LanguageSession provider;

        private Entry(LanguageSession provider) {
            this.provider = provider;
        }
        private volatile long generation;
        private ProjectLanguageIndex<?> current;
    }

    /**
     * Opens index ownership explicitly; repeated opens of the same active project reuse it.
     * Closes any previous session belonging to another project with the same normalized root.
     *
     * @param project project acquiring index ownership
     * @return existing active session for this project, or a newly opened session
     */
    public Session openProject(Project project) {
        synchronized (ownership) {
            Session existing = sessions.get(project);
            if (existing != null && !existing.closed)
                return existing;
            Path root = normalize(project.getPath());
            Session old = roots.get(root);
            if (old != null) {
                closeProject(old);
            }
            var session = new Session(root);
            sessions.put(project, session);
            roots.put(root, session);
            return session;
        }
    }

    /**
     * Returns existing ownership without opening a closed project.
     *
     * @param project project whose index ownership is requested
     * @return active session, or {@code null} if this project has no open session
     */
    public @Nullable Session session(Project project) {
        synchronized (ownership) {
            return sessions.get(project);
        }
    }

    /**
     * Rejects old live contexts when the project adopts refreshed configuration.
     *
     * @param session session whose configuration generation advances; {@code null}, closed or superseded
     *            sessions are ignored
     */
    public void refreshProject(Session session) {
        synchronized (ownership) {
            if (session == null || session.closed || roots.get(session.root) != session)
                return;
            session.configurationGeneration++;
            session.entries.values().forEach(entry -> {
                entry.generation++;
                entry.current = null;
            });
        }
    }

    /**
     * Rejects late work and releases caches without waiting for an operation lock.
     *
     * @param session session to close, or {@code null} to do nothing; repeated closure is safe
     */
    public void closeProject(Session session) {
        if (session == null)
            return;
        synchronized (ownership) {
            session.closed = true;
            session.entries.values().forEach(entry -> {
                entry.generation++;
                entry.current = null;
            });
            session.entries.clear();
            roots.remove(session.root, session);
            sessions.values().removeIf(value -> value == session);
        }
    }

    private ProjectIndexContext resolve(Project project) {
        Session session = session(project);
        long configuration = session == null ? 0 : session.configurationGeneration();
        ProjectIndexContext context = new DefaultProjectIndexContextResolver(_ -> null, runtime).resolve(project);
        return new ProjectIndexContext(project, context.projectRoot(), context.languages(), session, configuration);
    }

    private Entry entry(ProjectIndexContext context, String language) {
        LanguageSession provider = languageSession(context.project(), language);
        if (provider == null)
            throw new CancellationException("Language is no longer registered: " + language);
        synchronized (ownership) {
            check(context);
            return attachEntry(context.session(), language, provider);
        }
    }

    private Entry attachEntry(Session session, String language, LanguageSession provider) {
        Entry existing = session.entries.get(language);
        if (existing != null && existing.provider == provider)
            return existing;
        var entry = new Entry(provider);
        session.entries.put(language, entry);
        provider.onClose(() -> {
            synchronized (ownership) {
                entry.generation++;
                entry.current = null;
                session.entries.remove(language, entry);
            }
        });
        return entry;
    }

    private void check(ProjectIndexContext context) {
        context.checkActive();
        Session session = context.session();
        if (session == null || session.closed || roots.get(context.projectRoot()) != session
            || session.configurationGeneration != context.configurationGeneration())
            throw new CancellationException("Project index session is no longer active");
        refreshRegistrations(session);
    }

    private void refreshRegistrations(Session session) {
        long revision = runtime.registrationRevision();
        if (session != null && session.registrationRevision != revision) {
            session.registrationRevision = revision;
            session.entries.values().forEach(entry -> {
                entry.generation++;
                entry.current = null;
            });
        }
    }

    private long generation(ProjectIndexContext context, Entry entry) {
        synchronized (ownership) {
            check(context);
            if (entry.provider.isClosed())
                throw new CancellationException("Language session is closed");
            return entry.generation;
        }
    }

    private void publish(ProjectIndexContext context, Entry entry, long generation, ProjectLanguageIndex<?> index) {
        synchronized (ownership) {
            check(context);
            if (entry.generation != generation || entry.provider.isClosed())
                throw new CancellationException("Index invalidated");
            entry.current = index;
        }
    }

    private static ProjectIndexContext operationContext(ProjectIndexContext context, Entry entry, long generation) {
        return new ProjectIndexContext(context.project(), context.projectRoot(), context.languages(), context.session(),
            context.configurationGeneration(), () -> context.cancellationToken().isCancellationRequested()
                || entry.generation != generation || entry.provider.isClosed());
    }

    private static void lock(ProjectIndexContext context, Entry entry) {
        while (true) {
            context.checkActive();
            if (entry.provider.isClosed())
                throw new CancellationException("Language session is closed");
            try {
                if (entry.operation.tryLock(10, TimeUnit.MILLISECONDS))
                    return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Interrupted waiting for index");
            }
        }
    }

    private static void lock(Entry entry) {
        while (true) {
            if (entry.provider.isClosed())
                throw new CancellationException("Language session is closed");
            try {
                if (entry.operation.tryLock(10, TimeUnit.MILLISECONDS))
                    return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Interrupted waiting for index");
            }
        }
    }

    /**
     * Reports whether a project language session supplies an indexer.
     *
     * @param project project owning the language services
     * @param languageId host storage identifier or namespaced contribution identifier
     * @return whether the language session exposes a project indexer
     */
    public boolean hasIndexer(Project project, String languageId) {
        return getIndexer(project, languageId) != null;
    }

    /**
     * Returns the indexer owned by the project language session, or null when unsupported.
     *
     * @param project project owning the language services
     * @param languageId host storage identifier or namespaced contribution identifier
     * @return the session indexer, or null if the language or capability is unavailable
     */
    public @Nullable ProjectLanguageIndexer<?, ?> getIndexer(Project project, String languageId) {
        LanguageSession provider = languageSession(project, languageId);
        return provider == null ? null : provider.capability(LanguageCapabilities.INDEXER).orElse(null);
    }

    /**
     * Returns persistence owned by the project language session, or null when unsupported.
     *
     * @param project project owning the language services
     * @param languageId host storage identifier or namespaced contribution identifier
     * @return the session persistence service, or null if unavailable
     */
    public @Nullable ProjectLanguageIndexPersistence<?> getPersistence(Project project, String languageId) {
        LanguageSession provider = languageSession(project, languageId);
        return provider == null ? null : provider.capability(LanguageCapabilities.PERSISTENCE).orElse(null);
    }

    /**
     * Runs optional auxiliary indexing in the same owned language session as source indexing.
     *
     * @param project project owning the language services
     * @param languageId host storage identifier or namespaced contribution identifier
     * @param context project indexing inputs and cancellation controls
     */
    public void warmAdditionalIndexes(Project project, String languageId, ProjectIndexContext context) {
        LanguageSession provider = languageSession(project, languageId);
        if (provider != null) {
            provider.capability(LanguageCapabilities.INDEX_WARMUP).ifPresent(warmup -> warmup.warm(context));
        }
    }

    /**
     * Returns the current contributions, including languages loaded after the project opened.
     *
     * @return an immutable snapshot of contributions ordered by language ID
     */
    public List<LanguageContribution> languages() {
        return runtime.all();
    }

    /**
     * Registration revision used to invalidate captured index contexts after plugin changes.
     *
     * @return the current language registration revision
     */
    public long languageRegistrationRevision() {
        return runtime.registrationRevision();
    }

    /**
     * Uses the same priority and content matching as editor language selection.
     *
     * @param path file to recognize
     * @return the highest-ranked matching contribution, or empty if none matches
     */
    public Optional<LanguageContribution> findLanguage(Path path) {
        return runtime.find(path);
    }

    /**
     * Resolves a contribution's existing index storage identity through its indexer.
     *
     * @param project project owning the language services
     * @param contribution language whose index storage identity is needed
     * @return the indexer's language identifier, or the contribution ID if indexing is unsupported
     */
    public String indexLanguageId(Project project, LanguageContribution contribution) {
        ProjectLanguageIndexer<?, ?> indexer = getIndexer(project, contribution.id().value());
        return indexer == null ? contribution.id().value() : indexer.languageId();
    }

    /**
     * Refreshes live contributions while retaining explicitly supplied contexts for registered languages.
     *
     * @param previous captured context whose project index session must still be active
     * @return the refreshed context retaining the previous session and cancellation token
     */
    public ProjectIndexContext refreshContext(ProjectIndexContext previous) {
        synchronized (ownership) {
            Session session = previous.session();
            if (session == null || session.closed || roots.get(previous.projectRoot()) != session)
                throw new CancellationException("Project index session is no longer active");
        }
        ProjectIndexContext resolved = new DefaultProjectIndexContextResolver(_ -> previous.session(), runtime)
            .resolve(previous.project());
        Map<String, LanguageIndexContext> languages = new HashMap<>();
        for (LanguageContribution contribution : runtime.all()) {
            String id = indexLanguageId(previous.project(), contribution);
            LanguageIndexContext supplied = previous.language(id);
            if (supplied != null) {
                languages.put(id, supplied);
            }
        }
        languages.putAll(resolved.languages());
        return new ProjectIndexContext(previous.project(), previous.projectRoot(), languages, previous.session(),
            resolved.configurationGeneration(), previous.cancellationToken());
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
            refreshRegistrations(session);
            Entry entry = session == null ? null : session.entries.get(languageId);
            return entry == null || entry.provider.isClosed() ? null : entry.current;
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
            return entry == null || entry.provider.isClosed() ? null : entry.current;
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

        if (getIndexer(context.project(), languageId) == null)
            return null;
        Entry entry = entry(context, languageId);
        lock(context, entry);
        try {
            long generation = generation(context, entry);
            ProjectIndexContext operationContext = operationContext(context, entry, generation);
            ProjectLanguageIndex<?> current = current(context, languageId);
            if (current != null)
                return current;
            ProjectLanguageIndexer<?, ?> indexer = getIndexer(context.project(), languageId);
            if (indexer == null)
                return null;
            Collection<Path> files = collectFiles(languageContext, operationContext).stream()
                .filter(file -> ownsFile(languageId, file) && indexer.supports(file)).toList();
            ProjectLanguageKey key = key(projectRoot, languageId);
            ProjectLanguageIndex<?> result = loadPersisted(key, files, operationContext);
            boolean built = result == null;
            if (built) {
                result = buildUnchecked(indexer, operationContext, files);
            }
            publish(context, entry, generation, result);
            if (built) {
                savePersisted(context, entry, generation, key, result);
            }
            return result;
        } finally {
            entry.operation.unlock();
        }
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

        ProjectLanguageIndexer<?, ?> rawIndexer = getIndexer(context.project(), languageId);
        if (rawIndexer == null)
            return null;

        synchronized (ownership) {
            check(context);
            Entry existing = context.session().entries.get(languageId);
            if (existing != null) {
                existing.generation++;
                existing.current = null;
            }
        }
        Entry entry = entry(context, languageId);
        lock(context, entry);
        try {
            long generation = generation(context, entry);
            ProjectIndexContext operationContext = operationContext(context, entry, generation);
            ProjectLanguageIndex<?> rebuilt = buildUnchecked(rawIndexer, operationContext,
                collectFiles(languageContext, operationContext).stream()
                    .filter(file -> ownsFile(languageId, file) && rawIndexer.supports(file)).toList());
            publish(context, entry, generation, rebuilt);
            savePersisted(context, entry, generation, key(projectRoot, languageId), rebuilt);
            return rebuilt;
        } finally {
            entry.operation.unlock();
        }
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
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) getIndexer(context.project(), languageId);
        if (indexer == null)
            return null;
        if (!ownsFile(languageId, normalizedFile) || !indexer.supports(normalizedFile)) {
            removeFile(context, languageId, normalizedFile);
            return null;
        }

        Entry entry = entry(context, languageId);
        lock(context, entry);
        try {
            long generation = generation(context, entry);
            ProjectIndexContext operationContext = operationContext(context, entry, generation);
            I current = indexTyped(context, languageId);
            if (current == null)
                return null;
            operationContext.checkActive();
            F indexedFile = indexer.indexFile(operationContext, normalizedFile, readSource(normalizedFile));
            operationContext.checkActive();
            I updated = indexer.withUpdatedFile(current, normalizedFile, indexedFile);
            publish(context, entry, generation, updated);
            savePersisted(context, entry, generation, key(normalizedRoot, languageId), updated);
            return indexedFile;
        } finally {
            entry.operation.unlock();
        }
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
        ProjectLanguageIndexer<I, F> indexer = (ProjectLanguageIndexer<I, F>) getIndexer(context.project(), languageId);
        if (indexer == null)
            return;

        Entry entry = entry(context, languageId);
        lock(context, entry);
        try {
            long generation = generation(context, entry);
            ProjectIndexContext operationContext = operationContext(context, entry, generation);
            I current = indexTyped(context, languageId);
            if (current == null)
                return;
            I updated = indexer.withRemovedFile(current, normalizedFile);
            publish(context, entry, generation, updated);
            savePersisted(context, entry, generation, key(normalizedRoot, languageId), updated);
        } finally {
            entry.operation.unlock();
        }
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
            if (session == null)
                return;
            Entry entry = session.entries.get(languageId);
            if (entry != null) {
                entry.generation++;
                entry.current = null;
            }
        }
    }

    /**
     * Deletes a project language's persisted index through its registered persistence adapter.
     *
     * @param project the project whose files and configuration are used
     * @param languageId the stable language identifier
     */
    public void deletePersisted(Project project, String languageId) {
        LanguageSession provider = languageSession(project, languageId);
        if (provider == null)
            return;
        Session session;
        Entry entry;
        long generation;
        synchronized (ownership) {
            session = sessions.get(project);
            if (session == null)
                return;
            entry = attachEntry(session, languageId, provider);
            generation = ++entry.generation;
            entry.current = null;
        }
        ProjectLanguageIndexPersistence<?> persistence = getPersistence(project, languageId);
        lock(entry);
        try {
            if (persistence != null) {
                persistence.delete(session.root, replacement -> {
                    synchronized (ownership) {
                        if (!session.closed && roots.get(session.root) == session
                            && entry.generation == generation && !provider.isClosed()) {
                            replacement.run();
                        }
                    }
                });
            }
        } finally {
            entry.operation.unlock();
        }
    }

    /**
     * Evicts all in-memory language indexes for a project.
     *
     * @param project the project whose files and configuration are used
     */
    public void invalidateProject(Project project) {
        synchronized (ownership) {
            Session session = sessions.get(project);
            if (session != null) {
                session.entries.values().forEach(entry -> {
                    entry.generation++;
                    entry.current = null;
                });
            }
        }
    }

    private ProjectLanguageKey key(Path projectRoot, String languageId) {
        Objects.requireNonNull(languageId, "languageId");
        return new ProjectLanguageKey(normalize(projectRoot), languageId);
    }

    private boolean ownsFile(String languageId, Path file) {
        var id = new LanguageId(languageId.contains(":") ? languageId : "railroad:" + languageId);
        return runtime.find(file).map(contribution -> contribution.id().equals(id)).orElse(false);
    }

    private static Path normalize(Path path) {
        return FileUtils.normalizePath(Objects.requireNonNull(path, "path"));
    }

    private static Collection<Path> collectFiles(LanguageIndexContext languageContext, ProjectIndexContext context) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        collectFiles(files, languageContext.sourceRoots(), context);
        collectFiles(files, languageContext.generatedRoots(), context);
        return files;
    }

    private static void collectFiles(LinkedHashSet<Path> files, Collection<Path> roots, ProjectIndexContext context) {
        for (Path root : roots) {
            context.checkActive();
            Path normalizedRoot = normalize(root);
            if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot))
                continue;

            try (Stream<Path> paths = Files.walk(normalizedRoot)) {
                paths
                    .peek(_ -> context.checkActive())
                    .filter(Files::isRegularFile)
                    .map(ProjectLanguageIndexService::normalize)
                    .forEach(files::add);
            } catch (CancellationException exception) {
                throw exception;
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

    private @Nullable ProjectLanguageIndex<?> loadPersisted(
        ProjectLanguageKey key,
        Collection<Path> sourceFiles,
        ProjectIndexContext context
    ) {
        ProjectLanguageIndexPersistence<?> persistence = getPersistence(context.project(), key.languageId());
        if (persistence == null)
            return null;

        return persistence.loadIfCurrent(key.projectRoot(), sourceFiles, context::checkActive);
    }

    @SuppressWarnings("unchecked")
    private void savePersisted(
        ProjectIndexContext context,
        Entry entry,
        long generation,
        ProjectLanguageKey key,
        ProjectLanguageIndex<?> index
    ) {
        var persistence = (ProjectLanguageIndexPersistence<ProjectLanguageIndex<?>>) getPersistence(context.project(),
            key.languageId());
        if (persistence == null)
            return;
        ProjectIndexContext operationContext = operationContext(context, entry, generation);
        try {
            persistence.save(key.projectRoot(), index, replacement -> {
                synchronized (ownership) {
                    check(context);
                    if (entry.generation != generation || entry.current != index || entry.provider.isClosed())
                        throw new CancellationException("Index invalidated before persistence commit");
                    replacement.run();
                }
            }, operationContext::checkActive);
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(ProjectLanguageIndexService.class)
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
