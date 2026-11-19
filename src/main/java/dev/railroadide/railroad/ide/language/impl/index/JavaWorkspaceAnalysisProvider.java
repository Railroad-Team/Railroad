package dev.railroadide.railroad.ide.language.impl.index;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** Builds reusable Java analysis inputs exclusively from captured documents and configuration. */
public final class JavaWorkspaceAnalysisProvider implements AutoCloseable {
    private static final Path RUNNING_JDK = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
    private final Cache<WorkspaceKey, JavaWorkspaceAnalysis> workspaces;
    private final Cache<TextDocumentSnapshot, JavaProjectSemanticIndex.SourceFileIndex> files;
    private final Cache<WorkspaceConfigurationSnapshot, JavaSymbolIndex> binaries;
    private final ReentrantLock analysisLock = new ReentrantLock();
    private final Object publicationLock = new Object();
    private volatile boolean closed;
    private volatile long cacheGeneration;

    /** Creates bounded caches retaining at most four workspace results, 512 source revisions and two binary indexes. */
    public JavaWorkspaceAnalysisProvider() {
        this(() -> true);
    }

    private final BooleanSupplier active;
    private final BiFunction<List<Path>, Path, JavaSymbolIndex> binaryLoader;

    /**
     * Creates a cache with default library and JDK scanning and a configuration ownership guard.
     *
     * @param active reports whether this provider's configuration still owns analysis work
     */
    public JavaWorkspaceAnalysisProvider(BooleanSupplier active) {
        this(active, null, 4, 512, 2);
    }

    /**
     * Creates a cache with controlled binary loading and configuration ownership checks.
     *
     * @param active reports whether this provider's configuration still owns analysis work
     * @param binaryLoader loads binary symbols from ordered classpath roots and the selected JDK home
     */
    public JavaWorkspaceAnalysisProvider(
        BooleanSupplier active,
        BiFunction<List<Path>, Path, JavaSymbolIndex> binaryLoader
    ) {
        this(active, Objects.requireNonNull(binaryLoader), 4, 512, 2);
    }

    /**
     * Creates bounded caches with access-based eviction. Zero disables retention for a cache.
     * Limits bound retained entries, not the heap size of an individual input or caller-held result.
     * Loading is serialized with cancellable waits; disposal never waits for a loader.
     *
     * @param active configuration ownership guard
     * @param binaryLoader optional custom loader; null selects cooperative library/JDK scanning
     * @param workspaceLimit maximum captured workspace/request combinations
     * @param sourceLimit maximum indexed source revisions
     * @param binaryLimit maximum binary configurations
     */
    public JavaWorkspaceAnalysisProvider(
        BooleanSupplier active,
        BiFunction<List<Path>, Path, JavaSymbolIndex> binaryLoader,
        int workspaceLimit,
        int sourceLimit,
        int binaryLimit
    ) {
        this.active = Objects.requireNonNull(active, "active");
        this.binaryLoader = binaryLoader;
        workspaces = newCache(workspaceLimit);
        files = newCache(sourceLimit);
        binaries = newCache(binaryLimit);
    }

    private static <K, V> Cache<K, V> newCache(int limit) {
        return CacheBuilder.newBuilder().concurrencyLevel(1).maximumSize(limit).recordStats().build();
    }

    private void checkActive() {
        if (closed || !active.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Java analysis configuration is no longer active");
    }

    /**
     * Pins Java source declarations, source bodies, and binary symbols for a request workspace.
     * This method runs on a language worker; it never consults project models or the live source index.
     * Binary artifacts are read when their captured configuration is first analyzed and remain pinned
     * while that configuration is retained. External artifact replacement requires configuration refresh.
     *
     * @param workspace immutable workspace captured for the feature request
     * @return immutable analysis inputs shared by requests using that capture
     */
    public JavaWorkspaceAnalysis capture(WorkspaceSnapshot workspace) {
        return capture(workspace, null);
    }

    /**
     * Captures configured source dependencies and includes the requesting buffer even outside source roots.
     *
     * @param workspace immutable workspace captured for the feature request
     * @param request requesting document identity, or null for configured dependencies only
     * @return analysis inputs containing the request and its captured dependencies
     */
    public JavaWorkspaceAnalysis capture(WorkspaceSnapshot workspace, DocumentId request) {
        return capture(workspace, request, this::checkActive);
    }

    /**
     * Captures analysis using the request's cancellation and deadline throughout loading and lock waits.
     *
     * @param context captured workspace and execution controls, optionally selecting a document
     * @return complete immutable analysis inputs; cancelled loads are never published as workspace results
     */
    public JavaWorkspaceAnalysis capture(RequestContext context) {
        Objects.requireNonNull(context, "context");
        return capture(context.workspaceSnapshot(),
            context instanceof FeatureRequestContext feature ? feature.documentSnapshot().id() : null,
            context::checkActive);
    }

    private JavaWorkspaceAnalysis capture(WorkspaceSnapshot workspace, DocumentId request, Runnable requestCheck) {
        Objects.requireNonNull(workspace);
        long generation = cacheGeneration;
        Runnable check = () -> {
            requestCheck.run();
            checkActive();
            if (generation != cacheGeneration)
                throw new CancellationException("Java analysis caches were cleared");
        };
        check.run();
        try {
            while (!analysisLock.tryLock(25, TimeUnit.MILLISECONDS)) {
                check.run();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Java analysis cancelled while waiting for cache load");
        }
        try {
            check.run();
            Set<Path> roots = sourceRoots(workspace.configuration());
            DocumentSnapshot document = request == null ? null : workspace.document(request).orElseThrow();
            DocumentId extra = document != null && !includedSource(document, roots) ? request : null;
            var key = new WorkspaceKey(workspace, extra);
            JavaWorkspaceAnalysis analysis = workspaces.getIfPresent(key);
            if (analysis == null) {
                analysis = build(workspace, extra, roots, check);
                publish(workspaces, key, analysis, check);
            }
            check.run();
            return analysis;
        } finally {
            analysisLock.unlock();
        }
    }

    private JavaWorkspaceAnalysis build(
        WorkspaceSnapshot workspace,
        DocumentId request,
        Set<Path> roots,
        Runnable check
    ) {
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        Map<Path, String> texts = new HashMap<>();
        Map<Path, TextDocumentSnapshot> snapshots = new HashMap<>();
        var extractor = new JavaProjectSemanticExtractor();
        for (DocumentSnapshot document : workspace.documents().values()) {
            check.run();
            if (!(document instanceof TextDocumentSnapshot text) || !"java".equals(text.languageId()))
                continue;
            if (!text.id().equals(request) && !includedSource(text, roots))
                continue;
            Path path = text.uri().filePath().orElseGet(() -> Path.of(".railroad", "virtual", text.id().toString(),
                text.uri().fileName().orElse("Snapshot.java")).toAbsolutePath().normalize());
            JavaProjectSemanticIndex.SourceFileIndex indexed = files.getIfPresent(text);
            if (indexed == null) {
                indexed = extractor.extract(path, text, check);
                publish(files, text, indexed, check);
            }
            builder.putFile(indexed);
            texts.put(indexed.path(), text.text());
            snapshots.put(indexed.path(), text);
        }
        JavaProjectSemanticIndex sources = builder.build();
        WorkspaceConfigurationSnapshot configuration = workspace.configuration();
        List<Path> classpath = classpath(configuration);
        Path jdk = jdkHome(configuration);
        JavaSymbolIndex binary = binaries.getIfPresent(configuration);
        if (binary == null) {
            binary = binaryLoader == null
                ? new CompositeJavaSymbolIndex(List.of(JavaLibrarySymbolIndex.build(classpath, check),
                    JavaJdkSymbolIndex.build(jdk, check)))
                : binaryLoader.apply(classpath, jdk);
            publish(binaries, configuration, binary, check);
        }
        check.run();
        JavaSymbolIndex discovered = new JavaSourcePrecedenceIndex(sources, binary);
        JavaProjectSemanticIndex.Builder canonical = JavaProjectSemanticIndex.builder();
        for (var entry : snapshots.entrySet()) {
            check.run();
            var tree = JavaSyntaxParser.parse(entry.getValue(), check);
            var model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(tree, discovered, check);
            var indexed = extractor.extract(entry.getKey(), model, discovered, check);
            canonical.putFile(new JavaProjectSemanticIndex.SourceFileIndex(indexed.path(), indexed.packageName(),
                indexed.imports(), indexed.declaredSymbols(), sources.getFileIndex(entry.getKey()).sourceHash(),
                indexed.semanticShard()));
        }
        sources = canonical.build();
        check.run();
        return new JavaWorkspaceAnalysis(sources, new JavaSourcePrecedenceIndex(sources, binary), texts,
            classpath, jdk);
    }

    private <K, V> void publish(Cache<K, V> cache, K key, V value, Runnable check) {
        synchronized (publicationLock) {
            check.run();
            cache.put(key, value);
        }
    }

    /** Releases retained results and rejects publication from loads already in progress. */
    public void clearCaches() {
        synchronized (publicationLock) {
            cacheGeneration++;
            workspaces.invalidateAll();
            files.invalidateAll();
            binaries.invalidateAll();
        }
    }

    /** Releases retained results without waiting for scanners and rejects future captures. */
    @Override
    public void close() {
        synchronized (publicationLock) {
            closed = true;
            clearCaches();
        }
    }

    /**
     * Returns aggregate cache measurements without retaining keys, documents or analysis values.
     *
     * @return current entry counts and cumulative lookups/evictions
     */
    public CacheUsage cacheUsage() {
        var totals = workspaces.stats().plus(files.stats()).plus(binaries.stats());
        return new CacheUsage(workspaces.size(), files.size(), binaries.size(),
            totals.hitCount(), totals.missCount(), totals.evictionCount());
    }

    /**
     * Bounded cache occupancy and cumulative counters; explicit clearing is not an eviction.
     *
     * @param workspaces number of retained workspace analysis results
     * @param sourceFiles number of retained source revision indexes
     * @param binaries number of retained binary configuration indexes
     * @param hits cumulative successful lookups across all three caches
     * @param misses cumulative lookups without a retained entry across all three caches
     * @param evictions cumulative automatic entry removals across all three caches
     */
    public record CacheUsage(long workspaces, long sourceFiles, long binaries, long hits, long misses, long evictions) {
    }

    private record WorkspaceKey(WorkspaceSnapshot workspace, DocumentId extraDocument) {
    }

    private static boolean includedSource(DocumentSnapshot document, Set<Path> roots) {
        return document.uri().filePath().map(path -> roots.stream()
            .anyMatch(path.toAbsolutePath().normalize()::startsWith)).orElse(true);
    }

    private static Set<Path> sourceRoots(WorkspaceConfigurationSnapshot configuration) {
        Set<Path> roots = new HashSet<>();
        for (String module : configuration.moduleIds()) {
            String sources = configuration.settings().get("module." + module + ".sources");
            if (sources != null) {
                sources.lines().filter(line -> !line.isBlank())
                    .map(line -> line.replaceFirst(":(?:test:)?generated=(?:true|false)$", ""))
                    .map(Path::of).map(path -> path.toAbsolutePath().normalize()).forEach(roots::add);
            }
        }
        return Set.copyOf(roots);
    }

    /**
     * Collects normalized, distinct binary roots in sorted module order, excluding workspace output directories.
     * Within each module, classpath, module path and dependency roots retain their configured order.
     *
     * @param configuration captured module settings
     * @return immutable ordered binary roots
     */
    public static List<Path> classpath(WorkspaceConfigurationSnapshot configuration) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        Set<Path> outputs = new HashSet<>();
        for (String module : configuration.moduleIds()) {
            for (String kind : List.of("output", "testOutput")) {
                String output = configuration.settings().get("module." + module + "." + kind);
                if (output != null && !output.isBlank()) {
                    outputs.add(Path.of(output).toAbsolutePath().normalize());
                }
            }
        }
        for (String module : configuration.moduleIds().stream().sorted().toList()) {
            for (String kind : List.of("classpath", "modulePath", "dependencies")) {
                String value = configuration.settings().get("module." + module + "." + kind);
                if (value != null) {
                    value.lines().filter(line -> !line.isBlank()).map(Path::of)
                        .map(path -> path.toAbsolutePath().normalize()).filter(path -> !outputs.contains(path))
                        .forEach(roots::add);
                }
            }
        }
        return List.copyOf(roots);
    }

    /**
     * Selects the first configured JDK home in sorted module order, falling back to the running JDK.
     *
     * @param configuration captured module settings
     * @return absolute normalized JDK home
     */
    public static Path jdkHome(WorkspaceConfigurationSnapshot configuration) {
        return configuration.moduleIds().stream().sorted()
            .map(module -> configuration.settings().get("module." + module + ".jdkHome"))
            .filter(Objects::nonNull).filter(home -> !home.isBlank()).findFirst()
            .map(Path::of).map(path -> path.toAbsolutePath().normalize()).orElse(RUNNING_JDK);
    }
}
