package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService.Session;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.index.DefaultProjectIndexContextResolver;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;
import com.google.common.cache.CacheBuilder;

/**
 * Combines Java project, library, and JDK symbol indexes, caching library and JDK scans.
 */
public final class JavaAnalysisContextProvider implements AutoCloseable {
    private final JavaWorkspaceAnalysisProvider workspaceAnalysis = new JavaWorkspaceAnalysisProvider();
    private final ConcurrentMap<String, Cache> sessions = new ConcurrentHashMap<>();
    private static final String SESSION = "index.session";
    private static final String EPOCH = "index.binary.epoch";
    private volatile boolean closed;

    private static final class Cache {
        private final Session session;
        private final String epoch;
        private final long generation;
        private final com.google.common.cache.Cache<LibraryIndexKey, JavaSymbolIndex> libraries = CacheBuilder
            .newBuilder().concurrencyLevel(1).maximumSize(4).build();
        private final com.google.common.cache.Cache<Path, JavaJdkSymbolIndex> jdks = CacheBuilder.newBuilder()
            .concurrencyLevel(1).maximumSize(2).build();
        private final JavaWorkspaceAnalysisProvider workspace;
        private final BooleanSupplier active;
        private final Map<Object, ReentrantLock> loading = new ConcurrentHashMap<>();
        private volatile boolean disposed;
        private Cache(
            Session session,
            String epoch,
            BooleanSupplier active
        ) {
            this.session = session;
            this.epoch = epoch;
            this.generation = session.configurationGeneration();
            this.active = active;
            workspace = new JavaWorkspaceAnalysisProvider(active, this::binaries);
        }

        /** Lets workspace captures reuse this session's binary indexes instead of scanning and holding their own. */
        private JavaSymbolIndex binaries(List<Path> classpath, Path jdk) {
            Runnable check = this::checkActive;
            return new CompositeJavaSymbolIndex(List.of(libraryIndex(classpath, null, this, check),
                jdkIndex(jdk, this, check)));
        }

        private void checkActive() {
            if (disposed || !active.getAsBoolean() || Thread.currentThread().isInterrupted())
                throw new CancellationException("Java configuration not active");
        }

        private <K, V> V load(
            com.google.common.cache.Cache<K, V> cache,
            K key,
            String activityKey,
            Runnable activityCheck,
            Supplier<V> loader
        ) {
            activityCheck.run();
            checkActive();
            V value = cache.getIfPresent(key);
            if (value == null) {
                ReentrantLock lock = loading.computeIfAbsent(key, _ -> new ReentrantLock());
                acquire(lock, activityCheck);
                try {
                    value = cache.getIfPresent(key);
                    if (value == null) {
                        try (var activity = BackgroundActivities.shared().start(activityKey)) {
                            value = ScopedValue.where(ActivityProgress.CURRENT, activity).call(loader::get);
                        }
                        synchronized (this) {
                            activityCheck.run();
                            checkActive();
                            cache.put(key, value);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
            activityCheck.run();
            checkActive();
            return value;
        }

        private void acquire(ReentrantLock lock, Runnable activityCheck) {
            try {
                while (!lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                    activityCheck.run();
                    checkActive();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Interrupted waiting for a Java index load");
            }
        }

        private synchronized void dispose() {
            disposed = true;
            libraries.invalidateAll();
            jdks.invalidateAll();
            workspace.close();
        }
    }

    /**
     * Prepares artifact identity on a configuration worker, never on a feature request.
     *
     * @param session session that will own the prepared configuration
     * @param configuration captured module settings to fingerprint
     * @return configuration with the session token, a new binary epoch and artifact fingerprint
     * @throws CancellationException if the session closes before preparation completes
     */
    public WorkspaceConfigurationSnapshot prepareConfiguration(
        Session session,
        WorkspaceConfigurationSnapshot configuration
    ) {
        if (closed || session.isClosed())
            throw new CancellationException("Project closed");
        var values = new HashMap<>(configuration.settings());
        values.put(SESSION, session.id());
        values.put(EPOCH, UUID.randomUUID().toString());
        values.put("index.binary.fingerprint", JavaArtifactFingerprint.capture(
            JavaWorkspaceAnalysisProvider.classpath(configuration),
            JavaWorkspaceAnalysisProvider.jdkHome(configuration), () -> {
                if (closed || session.isClosed() || Thread.currentThread().isInterrupted())
                    throw new CancellationException("Java configuration preparation cancelled");
            }));
        if (closed || session.isClosed())
            throw new CancellationException("Project closed");
        return new WorkspaceConfigurationSnapshot(
            configuration.revision(), configuration.moduleIds(), values);
    }

    /**
     * Adopts a prepared configuration without waiting for older scans.
     *
     * @param session session owning the replacement analysis caches
     * @param configuration configuration returned by {@link #prepareConfiguration}
     * @throws CancellationException if the session is already closed
     */
    public void adoptConfiguration(
        Session session,
        WorkspaceConfigurationSnapshot configuration
    ) {
        if (closed || session.isClosed())
            throw new CancellationException("Project closed");
        String epoch = configuration.settings().get(EPOCH);
        var cache = new Cache(session, epoch, () -> {
            Cache current = sessions.get(session.id());
            return !closed && !session.isClosed() && current != null && current.epoch.equals(epoch);
        });
        Cache previous = sessions.put(session.id(), cache);
        if (previous != null) {
            previous.dispose();
        }
        workspaceAnalysis.clearCaches();
        if (closed || session.isClosed()) {
            sessions.remove(session.id(), cache);
            cache.dispose();
        }
    }

    /**
     * Releases strong project cache ownership without acquiring a scanner's monitor.
     *
     * @param session session whose caches are released, or {@code null} to do nothing
     */
    public void closeSession(Session session) {
        if (session != null) {
            Cache removed = sessions.remove(session.id());
            if (removed != null) {
                removed.dispose();
            }
        }
    }

    /** Releases standalone and session caches, rejecting future work without waiting for scans. */
    @Override
    public void close() {
        closed = true;
        sessions.forEach((id, cache) -> {
            if (sessions.remove(id, cache)) {
                cache.dispose();
            }
        });
        workspaceAnalysis.close();
    }

    /**
     * Returns the number of sessions currently retaining analysis caches.
     *
     * @return current session cache count
     */
    public int cachedSessions() {
        return sessions.size();
    }

    private Cache cache(Session session) {
        Cache cache = session == null ? null : sessions.get(session.id());
        if (closed || cache == null || session.isClosed())
            throw new CancellationException("Java configuration not active");
        return cache;
    }

    private JavaWorkspaceAnalysisProvider workspace(WorkspaceSnapshot workspace) {
        if (closed)
            throw new CancellationException("Java analysis provider closed");
        String id = workspace.configuration().settings().get(SESSION);
        if (id == null)
            return workspaceAnalysis; // Standalone/headless captures have no live project ownership.
        Cache cache = sessions.get(id);
        if (cache == null || cache.session.isClosed()
            || !cache.epoch.equals(workspace.configuration().settings().get(EPOCH)))
            throw new CancellationException("Captured Java configuration was superseded");
        return cache.workspace;
    }

    /**
     * Returns Java analysis inputs pinned to captured source documents and configuration.
     *
     * @param workspace immutable request workspace
     * @return captured Java source and dependency context
     */
    public JavaWorkspaceAnalysis index(WorkspaceSnapshot workspace) {
        return workspace(workspace).capture(workspace);
    }

    /**
     * Returns captured Java inputs including a requesting buffer outside configured source roots.
     *
     * @param workspace immutable request workspace
     * @param request identity of the requesting document
     * @return captured Java source and dependency context
     */
    public JavaWorkspaceAnalysis index(WorkspaceSnapshot workspace, DocumentId request) {
        return workspace(workspace).capture(workspace, request);
    }

    /**
     * Returns Java inputs with request cancellation and deadline checks during source and binary scans.
     *
     * @param context captured workspace and optional document selection
     * @return complete immutable inputs for the supplied capture
     */
    public JavaWorkspaceAnalysis index(RequestContext context) {
        Objects.requireNonNull(context, "context");
        context.checkActive();
        return workspace(context.workspaceSnapshot()).capture(context);
    }
    private final DefaultProjectIndexContextResolver resolver = new DefaultProjectIndexContextResolver(
        project -> Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(project));

    /**
     * Combines the available project, library, and JDK symbol indexes for Java analysis.
     *
     * @param project the project whose files and configuration are used
     * @param file requesting source file, or {@code null} for a project-wide lookup
     * @return the combined index, a single available index, or {@code null} if none exists
     */
    public @Nullable JavaSymbolIndex index(Project project, @Nullable Path file) {
        return index(resolver.resolve(project), file);
    }

    /** Resolves live diagnostics using the document's source attachment when present. */
    public @Nullable JavaSymbolIndex indexForDocument(Project project, DocumentSnapshot document) {
        return indexForDocument(resolver.resolve(project), document);
    }

    /** Resolves a captured diagnostics document in a live indexing session. */
    public @Nullable JavaSymbolIndex indexForDocument(ProjectIndexContext context, DocumentSnapshot document) {
        return index(context, document.uri().filePath().orElse(null), document);
    }

    /**
     * Combines the available project, library, and JDK symbol indexes for Java analysis.
     *
     * @param context the project indexing context
     * @return the combined index, a single available index, or {@code null} if none exists
     */
    public @Nullable JavaSymbolIndex index(ProjectIndexContext context) {
        return index(context, null);
    }

    /**
     * Combines the project's source index with the binaries visible to a requesting file.
     *
     * @param context the project indexing context
     * @param file requesting source file, or {@code null} for a project-wide lookup
     * @return the combined index, a single available index, or {@code null} if none exists
     */
    public @Nullable JavaSymbolIndex index(ProjectIndexContext context, @Nullable Path file) {
        return index(context, file, null);
    }

    private @Nullable JavaSymbolIndex index(
        ProjectIndexContext context,
        @Nullable Path file,
        @Nullable DocumentSnapshot document
    ) {
        Objects.requireNonNull(context, "context");
        context.checkActive();

        JavaProjectSemanticIndex projectIndex = Services.PROJECT_LANGUAGE_INDEX_SERVICE.indexTyped(context,
            JavaLanguageSupport.LANGUAGE_ID);
        JavaLanguageIndexContext javaContext = context.language(JavaLanguageSupport.LANGUAGE_ID,
            JavaLanguageIndexContext.class);

        Cache cache = cache(context.session());
        Runnable activityCheck = () -> {
            context.checkActive();
            cache.checkActive();
        };
        if (context.configurationGeneration() != cache.generation)
            throw new CancellationException("Live Java configuration was superseded");
        List<JavaSymbolIndex> delegates = new ArrayList<>();
        if (projectIndex != null) {
            delegates.add(projectIndex);
        }

        if (javaContext != null) {
            WorkspaceConfigurationSnapshot gradle = javaContext.gradleConfiguration();
            List<Path> roots;
            Path archive;
            if (gradle != null) {
                var scope = JavaWorkspaceAnalysisProvider.sourceContext(gradle, file, document);
                roots = scope.classpath(gradle);
                archive = scope.archive();
            } else {
                roots = libraryRoots(javaContext);
                var sources = JavaLibrarySources.shared();
                archive = (document == null ? sources.ownerArchive(file, roots) : sources.ownerArchive(document, roots))
                    .orElse(null);
            }
            Path jdk = gradle == null ? javaContext.jdkHome() : JavaWorkspaceAnalysisProvider.jdkHome(gradle);
            JavaSymbolIndex libraryIndex = libraryIndex(roots, archive, cache, activityCheck);
            if (!libraryIndex.declaredQualifiedNames().isEmpty()) {
                delegates.add(libraryIndex);
            }

            JavaJdkSymbolIndex jdkIndex = jdkIndex(jdk, cache, activityCheck);
            if (!jdkIndex.declaredQualifiedNames().isEmpty()) {
                delegates.add(jdkIndex);
            }

            logIndexSummary(javaContext, roots.size(), jdk, projectIndex, libraryIndex, jdkIndex);
        }

        activityCheck.run();
        if (cache != cache(context.session()))
            throw new CancellationException("Java configuration changed during scan");
        if (delegates.isEmpty())
            return null;
        if (delegates.size() == 1)
            return delegates.getFirst();
        return new CompositeJavaSymbolIndex(delegates);
    }

    private static List<Path> libraryRoots(JavaLanguageIndexContext context) {
        Set<Path> roots = new LinkedHashSet<>();
        roots.addAll(context.classpathRoots());
        roots.addAll(context.modulePathRoots());
        roots.addAll(context.dependencyRoots());
        return FileUtils.normalizePaths(List.copyOf(roots));
    }

    private static JavaSymbolIndex libraryIndex(
        List<Path> roots,
        @Nullable Path archive,
        Cache cache,
        Runnable activityCheck
    ) {
        var key = new LibraryIndexKey(roots, archive);
        return cache.load(cache.libraries, key, "railroad.activity.indexing_libraries", activityCheck, () -> {
            if (archive != null)
                return libraryIndex(roots, null, cache, activityCheck).forAttachedSource(archive, activityCheck);
            Railroad.LOGGER.warn(
                "Building Java library index: roots={}, javafxBaseRoots={}, javafxGraphicsRoots={}, sampleRoots={}",
                roots.size(),
                countRootsContaining(roots, "javafx-base"),
                countRootsContaining(roots, "javafx-graphics"),
                sampleRoots(roots));
            JavaLibrarySymbolIndex index = JavaLibrarySymbolIndex.build(roots, activityCheck);
            Railroad.LOGGER.warn(
                "Built Java library index: classes={}, hasObservableList={}, hasReadOnlyDoubleProperty={}, hasNode={}, hasScene={}",
                index.declaredQualifiedNames().size(),
                index.classStubsByQualifiedName().containsKey("javafx.collections.ObservableList"),
                index.classStubsByQualifiedName().containsKey("javafx.beans.property.ReadOnlyDoubleProperty"),
                index.classStubsByQualifiedName().containsKey("javafx.scene.Node"),
                index.classStubsByQualifiedName().containsKey("javafx.scene.Scene"));
            return index;
        });
    }

    private static JavaJdkSymbolIndex jdkIndex(Path jdkHome, Cache cache, Runnable activityCheck) {
        Path cacheKey = jdkHome == null
            ? Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()
            : FileUtils.normalizePath(jdkHome);
        return cache.load(cache.jdks, cacheKey, "railroad.activity.indexing_jdk", activityCheck, () -> {
            Railroad.LOGGER.warn("Building Java JDK index: jdkHome={}, cacheKey={}", jdkHome, cacheKey);
            JavaJdkSymbolIndex index = JavaJdkSymbolIndex.build(jdkHome, activityCheck);
            Railroad.LOGGER.warn(
                "Built Java JDK index: classes={}, hasJavaUtilList={}, hasJavaLangObject={}, hasJavaUtilCollection={}",
                index.declaredQualifiedNames().size(),
                index.classStubsByQualifiedName().containsKey("java.util.List"),
                index.classStubsByQualifiedName().containsKey("java.lang.Object"),
                index.classStubsByQualifiedName().containsKey("java.util.Collection"));
            return index;
        });
    }

    private static void logIndexSummary(
        JavaLanguageIndexContext context,
        int binaryRoots,
        Path jdkHome,
        @Nullable JavaProjectSemanticIndex projectIndex,
        JavaSymbolIndex libraryIndex,
        JavaJdkSymbolIndex jdkIndex
    ) {
        Railroad.LOGGER.warn(
            "Java analysis index context: sourceRoots={}, generatedRoots={}, binaryRoots={}, jdkHome={}",
            context.sourceRoots().size(),
            context.generatedRoots().size(),
            binaryRoots,
            jdkHome);
        Railroad.LOGGER.warn(
            "Java analysis index availability: projectClasses={}, libraryClasses={}, jdkClasses={}, hasObservableList={}, hasList={}, hasNode={}, hasReadOnlyDoubleProperty={}",
            projectIndex == null ? 0 : projectIndex.declaredQualifiedNames().size(),
            libraryIndex.declaredQualifiedNames().size(),
            jdkIndex.declaredQualifiedNames().size(),
            libraryIndex.classStubsByQualifiedName().containsKey("javafx.collections.ObservableList"),
            jdkIndex.classStubsByQualifiedName().containsKey("java.util.List"),
            libraryIndex.classStubsByQualifiedName().containsKey("javafx.scene.Node"),
            libraryIndex.classStubsByQualifiedName().containsKey("javafx.beans.property.ReadOnlyDoubleProperty"));
    }

    private static long countRootsContaining(List<Path> roots, String text) {
        return roots.stream()
            .map(Path::toString)
            .filter(path -> path.toLowerCase().contains(text.toLowerCase()))
            .count();
    }

    private static List<String> sampleRoots(List<Path> roots) {
        return Stream.concat(
            roots.stream().filter(path -> path.toString().toLowerCase().contains("javafx")).limit(8),
            roots.stream().limit(4))
            .map(Path::toString)
            .distinct()
            .limit(12)
            .toList();
    }

    private record LibraryIndexKey(List<Path> roots, Path archive) {
        private LibraryIndexKey {
            roots = List.copyOf(roots);
        }
    }
}
