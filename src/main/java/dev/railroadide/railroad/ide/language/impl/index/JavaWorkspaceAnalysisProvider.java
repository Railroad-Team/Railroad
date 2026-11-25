package dev.railroadide.railroad.ide.language.impl.index;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import com.google.common.cache.CacheStats;

/**
 * Builds reusable Java analysis inputs exclusively from captured documents and configuration.
 */
public final class JavaWorkspaceAnalysisProvider implements AutoCloseable {
    private static final Path RUNNING_JDK = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
    private static final ExecutorService BINARY_LOADS = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("railroad-java-binaries-", 0).factory());
    private final Cache<WorkspaceKey, WorkspaceBuild> workspaces;
    private final Cache<TextDocumentSnapshot, JavaProjectSemanticIndex.SourceFileIndex> files;
    private final Cache<BinaryKey, JavaSymbolIndex> binaries;
    private final Map<BinaryKey, CompletableFuture<JavaSymbolIndex>> binaryLoads = new HashMap<>();
    private final ReentrantLock analysisLock = new ReentrantLock();
    private final Object publicationLock = new Object();
    private volatile boolean closed;
    private volatile long cacheGeneration;

    /**
     * Creates bounded caches retaining at most four workspace results, 512 source revisions and two binary indexes.
     */
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

            DocumentSnapshot document = request == null ? null : workspace.document(request).orElseThrow();

            Path file = document == null ? null : document.uri().filePath().orElse(null);
            SourceContext scope = sourceContext(workspace.configuration(), file, document);
            Set<Path> roots = scope.sourceSet() == null
                ? sourceRoots(workspace.configuration())
                : scope.sourceSet().visibleRoots();
            DocumentId extra = document != null && !includedSource(document, roots, scope, workspace.configuration())
                ? request
                : null;
            if (scope.archive() != null) {
                extra = request;
            }

            var key = new WorkspaceKey(workspace, extra, scope);
            WorkspaceBuild result = workspaces.getIfPresent(key);
            if (result == null) {
                WorkspaceBuild previous = workspaces.asMap().entrySet().stream()
                    .filter(entry -> key.sameScope(entry.getKey()))
                    .max(Comparator.comparingLong(entry -> entry.getKey().workspace().revision()))
                    .map(Map.Entry::getValue).orElse(null);
                result = build(workspace, extra, roots, scope, previous, check);
                publish(workspaces, key, result, check);
            }

            check.run();
            return result.analysis();
        } finally {
            analysisLock.unlock();
        }
    }

    private WorkspaceBuild build(
        WorkspaceSnapshot workspace,
        DocumentId request,
        Set<Path> roots,
        SourceContext scope,
        WorkspaceBuild previous,
        Runnable check
    ) {
        WorkspaceConfigurationSnapshot configuration = workspace.configuration();
        List<Path> classpath = scope.classpath(configuration);
        Path jdk = scope.jdkHome(configuration);
        CompletableFuture<JavaSymbolIndex> binaryLoad = loadBinaries(
            new BinaryKey(configuration, classpath, scope.archive(), jdk), jdk,
            check);
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        Map<Path, String> texts = new HashMap<>();
        Map<Path, DiscoveredSource> inputs = new HashMap<>();
        var extractor = new JavaProjectSemanticExtractor();
        for (DocumentSnapshot document : workspace.documents().values()) {
            check.run();
            if (!(document instanceof TextDocumentSnapshot text) || !"java".equals(text.languageId()))
                continue;

            if (!text.id().equals(request) && !includedSource(text, roots, scope, configuration))
                continue;

            Path path = text.uri().filePath().orElseGet(() -> Path.of(".railroad", "virtual", text.id().toString(),
                text.uri().fileName().orElse("Snapshot.java")).toAbsolutePath().normalize());
            DiscoveredSource input = previous == null ? null : previous.inputs().get(path);
            if (input == null || input.snapshot() != text) {
                JavaProjectSemanticIndex.SourceFileIndex indexed = files.getIfPresent(text);
                if (indexed == null) {
                    indexed = extractor.discoverTypes(path, text, check);
                    publish(files, text, indexed, check);
                }

                input = new DiscoveredSource(text, indexed);
            }

            builder.putFile(input.types());
            texts.put(input.types().path(), text.text());
            inputs.put(input.types().path(), input);
        }

        JavaProjectSemanticIndex sources = builder.build();
        JavaSymbolIndex binary = awaitBinaries(binaryLoad, check);
        binary.attachedSourceTexts().forEach(texts::putIfAbsent);
        check.run();
        boolean sameBinaries = previous != null && previous.binary() == binary;
        JavaSourcePrecedenceIndex discovered = sameBinaries
            ? previous.symbols().withSources(sources)
            : new JavaSourcePrecedenceIndex(sources, binary);
        boolean reuse = sameBinaries && inputs.size() == previous.inputs().size()
            && inputs.entrySet().stream().allMatch(e -> e.getValue().sameTypes(previous.inputs().get(e.getKey())));
        JavaProjectSemanticIndex.Builder canonical = JavaProjectSemanticIndex.builder();
        for (Map.Entry<Path, DiscoveredSource> entry : inputs.entrySet()) {
            check.run();
            if (reuse && previous.inputs().get(entry.getKey()) == entry.getValue()) {
                canonical.putFile(previous.analysis().sourceIndex().getFileIndex(entry.getKey()));
                continue;
            }

            var tree = JavaSyntaxParser.parse(entry.getValue().snapshot(), check);
            var model = JavaSemanticAnalyzer.analyzeProjectDeclarations(tree, discovered, check);
            JavaProjectSemanticIndex.SourceFileIndex indexed = extractor.extractProjectDeclarations(entry.getKey(),
                model, discovered, check);
            canonical.putFile(new JavaProjectSemanticIndex.SourceFileIndex(indexed.path(), indexed.packageName(),
                indexed.imports(), indexed.declaredSymbols(), sources.getFileIndex(entry.getKey()).sourceHash(),
                indexed.semanticShard()));
        }

        sources = canonical.build();
        check.run();
        JavaSourcePrecedenceIndex symbols = discovered.withSources(sources);
        var analysis = new JavaWorkspaceAnalysis(sources, symbols, texts, classpath, jdk);
        return new WorkspaceBuild(analysis, Map.copyOf(inputs), binary, symbols);
    }

    /**
     * Starts loading the binary index alongside source indexing when needed. Requests with the same captured ordered
     * classpath share the load. It is independent of request cancellation and deadlines, so a large classpath
     * that outlasts one request's budget still completes and is cached for the next.
     */
    private CompletableFuture<JavaSymbolIndex> loadBinaries(
        BinaryKey key,
        Path jdk,
        Runnable check
    ) {
        synchronized (publicationLock) {
            check.run();
            JavaSymbolIndex cached = binaries.getIfPresent(key);
            if (cached != null)
                return CompletableFuture.completedFuture(cached);

            CompletableFuture<JavaSymbolIndex> load = binaryLoads.get(key);
            return load == null ? startBinaryLoad(key, jdk) : load;
        }
    }

    private JavaSymbolIndex awaitBinaries(CompletableFuture<JavaSymbolIndex> load, Runnable check) {
        while (true) {
            check.run();
            try {
                return load.get(25, TimeUnit.MILLISECONDS);
            } catch (TimeoutException _) {
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Java analysis cancelled while waiting for binary index");
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof RuntimeException runtime)
                    throw runtime;

                if (exception.getCause() instanceof Error error)
                    throw error;

                throw new IllegalStateException("Failed to load Java binary index", exception.getCause());
            }
        }
    }

    private CompletableFuture<JavaSymbolIndex> startBinaryLoad(
        BinaryKey key,
        Path jdk
    ) {
        long generation = cacheGeneration;
        Runnable loadCheck = () -> {
            checkActive();
            if (generation != cacheGeneration)
                throw new CancellationException("Java analysis caches were cleared");
        };

        CompletableFuture<JavaSymbolIndex> load;
        if (key.archive() == null && binaryLoader != null) {
            load = CompletableFuture.supplyAsync(() -> binaryLoader.apply(key.classpath(), jdk), BINARY_LOADS);
        } else if (key.archive() == null) {
            load = CompletableFuture.supplyAsync(() -> {
                try (BackgroundActivities.Handle activity = BackgroundActivities.shared()
                    .start("railroad.activity.indexing_libraries")) {
                    return ScopedValue.where(ActivityProgress.CURRENT, activity)
                        .call(() -> new CompositeJavaSymbolIndex(
                            List.of(JavaLibrarySymbolIndex.build(key.classpath(), loadCheck),
                                JavaJdkSymbolIndex.build(jdk, loadCheck))));
                }
            }, BINARY_LOADS);
        } else {
            CompletableFuture<JavaSymbolIndex> base = loadBinaries(
                new BinaryKey(key.configuration(), key.classpath(), null, jdk), jdk, loadCheck);
            load = base.thenApplyAsync(index -> index.forAttachedSource(key.archive(), loadCheck), BINARY_LOADS);
        }

        binaryLoads.put(key, load);
        load.whenComplete((index, failure) -> {
            synchronized (publicationLock) {
                binaryLoads.remove(key, load);
                if (failure == null && !closed && active.getAsBoolean() && generation == cacheGeneration) {
                    binaries.put(key, index);
                }
            }
        });

        return load;
    }

    private <K, V> void publish(Cache<K, V> cache, K key, V value, Runnable check) {
        synchronized (publicationLock) {
            check.run();
            cache.put(key, value);
        }
    }

    /**
     * Releases retained results and rejects publication from loads already in progress.
     */
    public void clearCaches() {
        synchronized (publicationLock) {
            cacheGeneration++;
            workspaces.invalidateAll();
            files.invalidateAll();
            binaries.invalidateAll();
            binaryLoads.clear();
        }
    }

    /**
     * Releases retained results without waiting for scanners and rejects future captures.
     */
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
        CacheStats totals = workspaces.stats().plus(files.stats()).plus(binaries.stats());
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

    private record WorkspaceKey(WorkspaceSnapshot workspace, DocumentId extraDocument, SourceContext scope) {
        private boolean sameScope(WorkspaceKey other) {
            return workspace.projectId().equals(other.workspace.projectId())
                && workspace.configuration().equals(other.workspace.configuration())
                && Objects.equals(extraDocument, other.extraDocument) && Objects.equals(scope, other.scope);
        }
    }

    private record WorkspaceBuild(
        JavaWorkspaceAnalysis analysis,
        Map<Path, DiscoveredSource> inputs,
        JavaSymbolIndex binary,
        JavaSourcePrecedenceIndex symbols
    ) {
    }

    private record DiscoveredSource(TextDocumentSnapshot snapshot, JavaProjectSemanticIndex.SourceFileIndex types) {
        private boolean sameTypes(DiscoveredSource other) {
            return this == other || other != null && Objects.equals(types.packageName(), other.types.packageName())
                && types.declaredSymbols().equals(other.types.declaredSymbols());
        }
    }

    private record BinaryKey(
        WorkspaceConfigurationSnapshot configuration,
        List<Path> classpath,
        Path archive,
        Path jdk
    ) {
    }

    private static boolean includedSource(
        DocumentSnapshot document,
        Set<Path> roots,
        SourceContext scope,
        WorkspaceConfigurationSnapshot configuration
    ) {
        if (document.origin().backing() == BackingKind.ARCHIVE_ENTRY
            || document.origin().provenance() == ProvenanceKind.ATTACHED_SOURCE)
            return false;

        if (scope.sourceSet() != null)
            return document.uri().filePath()
                .filter(path -> JavaSourceSets.of(configuration).visible(scope.sourceSet(), path)).isPresent();

        return document.uri().filePath().map(path -> roots.stream()
            .anyMatch(path.toAbsolutePath().normalize()::startsWith)).orElse(true);
    }

    private static Set<Path> sourceRoots(WorkspaceConfigurationSnapshot configuration) {
        Set<Path> roots = new HashSet<>();
        for (String module : configuration.moduleIds()) {
            roots.addAll(sourceRoots(configuration, module));
        }

        return Set.copyOf(roots);
    }

    private static List<Path> sourceRoots(WorkspaceConfigurationSnapshot configuration, String module) {
        String sources = configuration.settings().getOrDefault("module." + module + ".sources", "");
        return sources.lines().filter(line -> !line.isBlank())
            .map(line -> line.replaceFirst(":(?:test:)?generated=(?:true|false)$", ""))
            .map(Path::of).map(path -> path.toAbsolutePath().normalize()).toList();
    }

    private static String moduleForPath(WorkspaceConfigurationSnapshot configuration, Path path) {
        if (path == null)
            return null;

        Path file = path.toAbsolutePath().normalize();
        String owner = null;
        int bestSource = -1;
        int bestDirectory = -1;
        for (String module : configuration.moduleIds().stream().sorted().toList()) {
            String prefix = "module." + module + ".";
            Map<String, String> settings = configuration.settings();
            if (!settings.containsKey(prefix + "classpath") && !settings.containsKey(prefix + "modulePath")
                && !settings.containsKey(prefix + "dependencies"))
                continue;

            String directory = settings.get(prefix + "directory");
            int directoryDepth = directory == null || directory.isBlank()
                ? -1
                : matchingDepth(file, Path.of(directory).toAbsolutePath().normalize());
            int sourceDepth = sourceRoots(configuration, module).stream()
                .mapToInt(root -> matchingDepth(file, root)).max().orElse(-1);
            if (sourceDepth > bestSource || sourceDepth == bestSource && directoryDepth > bestDirectory) {
                owner = module;
                bestSource = sourceDepth;
                bestDirectory = directoryDepth;
            }
        }

        return owner;
    }

    private static int matchingDepth(Path file, Path root) {
        return file.startsWith(root) ? root.getNameCount() : -1;
    }

    /**
     * Collects normalized, distinct binary roots in sorted module order, excluding workspace output directories.
     * Within each module, compile classpath and module path roots retain their configured order.
     *
     * @param configuration captured module settings
     * @return immutable ordered binary roots
     */
    public static List<Path> classpath(WorkspaceConfigurationSnapshot configuration) {
        return classpath(configuration, null);
    }

    /**
     * Selects the classpath for the module owning a captured source file.
     *
     * @param configuration captured module settings
     * @param file requesting source file, or null for the workspace fallback
     * @return ordered binary roots for its module, or the workspace roots if no module owns the file
     */
    public static List<Path> classpathForFile(WorkspaceConfigurationSnapshot configuration, Path file) {
        return sourceContext(configuration, file, null).classpath(configuration);
    }

    /**
     * Identifies the importing module and the binary owning an attached source document, when available.
     *
     * @param configuration captured module settings
     * @param file requesting source file, or null for the workspace fallback
     * @param document requesting document, or null to identify an attached source by its file alone
     * @return module, owning archive and importing source set of the source
     */
    public static SourceContext sourceContext(
        WorkspaceConfigurationSnapshot configuration,
        Path file,
        DocumentSnapshot document
    ) {
        var sets = JavaSourceSets.of(configuration);
        JavaSourceSets.Scope owner = sets.owner(file);
        String module = owner == null ? moduleForPath(configuration, file) : owner.module();
        if (module != null && (document == null || document.origin().provenance() != ProvenanceKind.ATTACHED_SOURCE))
            return new SourceContext(module, null, owner);

        if (document == null && file == null)
            return new SourceContext(null, null);

        List<Path> roots = classpath(configuration);
        var sources = JavaLibrarySources.shared();
        Path archive = (document == null ? sources.ownerArchive(file, roots) : sources.ownerArchive(document, roots))
            .orElse(null);
        String importer = archive == null ? module : moduleForArchive(configuration, archive);
        return new SourceContext(importer, archive, sets.importing(importer, archive));
    }

    /**
     * Classpath ownership for a project source or an attached dependency source.
     *
     * @param module module owning or importing the source, or null for the workspace fallback
     * @param archive binary owning an attached source, or null for a project source
     * @param sourceSet source set importing the archive, or null when unknown
     */
    public record SourceContext(String module, Path archive, JavaSourceSets.Scope sourceSet) {
        /**
         * Creates a context without a known importing source set.
         *
         * @param module module owning or importing the source, or null for the workspace fallback
         * @param archive binary owning an attached source, or null for a project source
         */
        public SourceContext(String module, Path archive) {
            this(module, archive, null);
        }

        /**
         * Returns the JDK used to compile this source.
         *
         * @param configuration captured module settings
         * @return the source set's JDK home, or the workspace JDK home when the source set has none
         */
        public Path jdkHome(WorkspaceConfigurationSnapshot configuration) {
            return sourceSet != null && sourceSet.jdkHome() != null
                ? sourceSet.jdkHome()
                : JavaWorkspaceAnalysisProvider.jdkHome(configuration);
        }

        /**
         * Returns the ordered binary roots for this source.
         *
         * @param configuration captured module settings
         * @return the source set's classpath, or the module's roots honouring its main compile versions
         */
        public List<Path> classpath(WorkspaceConfigurationSnapshot configuration) {
            if (sourceSet != null)
                return sourceSet.classpath();

            List<Path> roots = JavaWorkspaceAnalysisProvider.classpath(configuration, module);
            if (module == null || archive == null)
                return roots;

            String prefix = "module." + module + ".";
            Map<String, String> settings = configuration.settings();
            String selected = settings.getOrDefault(prefix + "mainCompileArtifacts", "");
            if (selected.lines().map(Path::of).noneMatch(archive::equals))
                return roots;

            Set<Path> excluded = settings.getOrDefault(prefix + "otherCompileVersions", "").lines().map(Path::of)
                .collect(Collectors.toSet());
            return roots.stream().filter(path -> !excluded.contains(path)).toList();
        }
    }

    private static String moduleForArchive(WorkspaceConfigurationSnapshot configuration, Path archive) {
        List<String> owners = configuration.moduleIds().stream()
            .filter(module -> classpath(configuration, module).contains(archive)).sorted().toList();
        return owners.size() == 1 ? owners.getFirst() : null;
    }

    private static List<Path> classpath(WorkspaceConfigurationSnapshot configuration, String selectedModule) {
        Set<Path> roots = new LinkedHashSet<>();
        Set<Path> outputs = new HashSet<>(JavaSourceSets.of(configuration).outputs());
        for (String module : configuration.moduleIds()) {
            for (String kind : List.of("output", "testOutput")) {
                String output = configuration.settings().get("module." + module + "." + kind);
                if (output != null && !output.isBlank()) {
                    outputs.add(Path.of(output).toAbsolutePath().normalize());
                }
            }
        }

        List<String> modules = selectedModule == null
            ? configuration.moduleIds().stream().sorted().toList()
            : List.of(selectedModule);
        for (String module : modules) {
            String prefix = "module." + module + ".";
            Map<String, String> settings = configuration.settings();
            List<String> kinds = settings.containsKey(prefix + "classpath")
                || settings.containsKey(prefix + "modulePath")
                    ? List.of("classpath", "modulePath")
                    : List.of("dependencies");
            for (String kind : kinds) {
                String value = settings.get(prefix + kind);
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
     * Selects the first configured JDK home in sorted module order, then the Gradle or running JDK.
     *
     * @param configuration captured module settings
     * @return absolute normalized JDK home
     */
    public static Path jdkHome(WorkspaceConfigurationSnapshot configuration) {
        String home = configuration.moduleIds().stream().sorted()
            .map(module -> configuration.settings().get("module." + module + ".jdkHome"))
            .filter(Objects::nonNull).filter(value -> !value.isBlank()).findFirst()
            .orElse(configuration.settings().get("gradle.jdkHome"));
        return home == null || home.isBlank() ? RUNNING_JDK : Path.of(home).toAbsolutePath().normalize();
    }
}
