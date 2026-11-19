package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticNavigationResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Immutable Java analysis inputs derived from one workspace capture. Source lookup never
 * falls back to disk; library and JDK symbols are pinned for the captured configuration.
 */
public final class JavaWorkspaceAnalysis implements JavaSourceSnapshotIndex {
    private final JavaProjectSemanticIndex sourceIndex;
    private final JavaSymbolIndex symbols;
    private final Map<Path, String> sourceTexts;
    private final List<Path> classpath;
    private final Path jdkHome;
    private final ReentrantLock graphLock = new ReentrantLock();
    private volatile WorkspaceSymbolGraph declarationGraph;
    private volatile WorkspaceSymbolGraph resolvedGraph;
    /**
     * Copies source text and dependency paths so later owners cannot change request inputs.
     *
     * @param sourceIndex captured source declarations
     * @param symbols immutable combined symbols
     * @param sourceTexts captured source text
     * @param classpath ordered dependency roots
     * @param jdkHome selected JDK home
     */
    public JavaWorkspaceAnalysis(
        JavaProjectSemanticIndex sourceIndex,
        JavaSymbolIndex symbols,
        Map<Path, String> sourceTexts,
        List<Path> classpath,
        Path jdkHome
    ) {
        this.sourceIndex = Objects.requireNonNull(sourceIndex);
        this.symbols = Objects.requireNonNull(symbols);
        this.sourceTexts = Map.copyOf(sourceTexts);
        this.classpath = List.copyOf(classpath);
        this.jdkHome = Objects.requireNonNull(jdkHome);
    }

    /**
     * Returns the source declarations pinned by this capture.
     *
     * @return captured source declaration index
     */
    public JavaProjectSemanticIndex sourceIndex() {
        return sourceIndex;
    }
    /**
     * Returns the combined symbols pinned by this capture.
     *
     * @return captured combined source and binary symbol index
     */
    public JavaSymbolIndex symbols() {
        return symbols;
    }
    /**
     * Returns source text copied when this analysis was captured.
     *
     * @return immutable captured source text keyed by path
     */
    public Map<Path, String> sourceTexts() {
        return sourceTexts;
    }
    /**
     * Returns the dependency roots pinned by this workspace capture.
     *
     * @return immutable ordered dependency roots captured for this analysis
     */
    public List<Path> classpath() {
        return classpath;
    }
    /**
     * Returns the JDK home pinned by this workspace capture.
     *
     * @return captured JDK home path
     */
    public Path jdkHome() {
        return jdkHome;
    }

    @Override
    public List<SemanticIndexShard> semanticShards() {
        return symbols.semanticShards();
    }

    @Override
    public List<SemanticIndexShard> semanticShards(Runnable activityCheck) {
        return symbols.semanticShards(activityCheck);
    }

    /**
     * Captures declaration and binary graph facts. Source references require {@link #resolvedGraph(Runnable)};
     * constructing completion inputs never triggers whole-workspace body resolution.
     *
     * @return immutable declaration graph for this capture
     */
    public WorkspaceSymbolGraph graph() {
        ActivityCheckpoint.checkInterrupted();
        var cached = declarationGraph;
        if (cached != null)
            return cached;
        lockGraph(ActivityCheckpoint::checkInterrupted);
        try {
            if (declarationGraph == null) {
                var result = WorkspaceSymbolGraph.of(semanticShards());
                ActivityCheckpoint.checkInterrupted();
                declarationGraph = result;
            }
            return declarationGraph;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Resolves all captured source bodies against these captured source and binary declarations.
     * Runs on a language worker, with cooperative cancellation and no live filesystem reads.
     * The returned immutable graph has complete available facts, including partial-program omissions;
     * cancellation throws without publishing or retaining a partially enriched graph. Successful
     * construction is single-flight and retained by this captured analysis until its owner releases it.
     *
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return cached immutable graph including resolved source references and override relations
     */
    public WorkspaceSymbolGraph resolvedGraph(Runnable activityCheck) {
        Objects.requireNonNull(activityCheck).run();
        var cached = resolvedGraph;
        if (cached != null) {
            activityCheck.run();
            return cached;
        }
        lockGraph(activityCheck);
        try {
            activityCheck.run();
            if (resolvedGraph == null) {
                var result = buildResolvedGraph(activityCheck);
                activityCheck.run();
                resolvedGraph = result;
            }
            activityCheck.run();
            return resolvedGraph;
        } finally {
            graphLock.unlock();
        }
    }

    private void lockGraph(Runnable check) {
        try {
            while (!graphLock.tryLock(25, TimeUnit.MILLISECONDS)) {
                check.run();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Java graph capture interrupted while waiting for resolution");
        }
    }

    private WorkspaceSymbolGraph buildResolvedGraph(Runnable activityCheck) {
        Map<DocumentId, SemanticIndexShard> shards = new LinkedHashMap<>();
        for (var shard : semanticShards(activityCheck)) {
            activityCheck.run();
            shards.put(shard.documentId(), shard);
        }
        var extractor = new JavaProjectSemanticExtractor();
        for (var file : sourceIndex.files().values()) {
            activityCheck.run();
            var resolved = resolveShard(extractor, file, activityCheck);
            shards.put(resolved.documentId(), resolved);
        }
        var result = WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(shards.values(), activityCheck),
            activityCheck);
        activityCheck.run();
        return result;
    }

    /**
     * Finds the navigable declarations of the symbol at an offset, resolving only the requesting document's body
     * against the captured declarations. Library declarations live in archives and are never navigable, so neither
     * the whole-workspace graph nor binary declarations are needed.
     *
     * @param workspace workspace capture this analysis was built from
     * @param source captured revision containing the offset
     * @param offset zero-based UTF-16 offset in the source revision
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return ranked targets, or an empty list when the offset is unresolved, ambiguous, or not navigable
     */
    public List<DefinitionTarget> definitionsAt(
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        int offset,
        Runnable activityCheck
    ) {
        Objects.requireNonNull(activityCheck).run();
        Map<DocumentId, SemanticIndexShard> shards = new LinkedHashMap<>();
        JavaProjectSemanticIndex.SourceFileIndex requested = null;
        for (var file : sourceIndex.files().values()) {
            activityCheck.run();
            var shard = file.semanticShard();
            if (shard == null)
                continue;
            shards.put(shard.documentId(), shard);
            if (shard.documentId().equals(source.id())) {
                requested = file;
            }
        }
        if (requested == null)
            return List.of();

        var resolved = resolveShard(new JavaProjectSemanticExtractor(), requested, activityCheck);
        shards.put(resolved.documentId(), resolved);
        var graph = WorkspaceSymbolGraph.of(shards.values(), activityCheck);
        return SemanticNavigationResolver.definitionAt(graph, workspace, source, offset);
    }

    private SemanticIndexShard resolveShard(
        JavaProjectSemanticExtractor extractor,
        JavaProjectSemanticIndex.SourceFileIndex file,
        Runnable activityCheck
    ) {
        var shard = file.semanticShard();
        if (shard == null)
            throw new IllegalStateException("Captured source has no semantic shard: " + file.path());
        var origin = shard.declarations().stream().flatMap(symbol -> symbol.locations().stream())
            .filter(location -> location.uri().equals(shard.uri())).map(location -> location.origin())
            .findFirst().orElseGet(() -> DocumentOrigin.unspecified(shard.uri()));
        String text = Objects.requireNonNull(sourceTexts.get(file.path()), "Captured source text");
        var snapshot = new TextDocumentSnapshot(shard.documentId(), shard.uri(), shard.documentVersion(),
            "java", origin, text, StandardCharsets.UTF_8);
        var tree = JavaSyntaxParser.parse(snapshot, activityCheck);
        var model = JavaSemanticAnalyzer.analyzeFacts(tree, this, activityCheck);
        var resolved = extractor.extract(file.path(), model, this, activityCheck).semanticShard();
        activityCheck.run();
        return resolved;
    }

    /**
     * Resolves captured source bodies with thread-interruption checks.
     *
     * @return cached immutable graph including resolved source references and override relations
     */
    public WorkspaceSymbolGraph resolvedGraph() {
        return resolvedGraph(ActivityCheckpoint::checkInterrupted);
    }

    @Override
    public Optional<String> sourceText(Path path) {
        return Optional.ofNullable(sourceTexts.get(path));
    }
    @Override
    public Set<String> declaredQualifiedNames() {
        return symbols.declaredQualifiedNames();
    }
    @Override
    public Set<String> typeNames() {
        return symbols.typeNames();
    }
    @Override
    public boolean containsPackage(String name) {
        return symbols.containsPackage(name);
    }
    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return symbols.classStubsByQualifiedName();
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String name) {
        return symbols.lookupSimpleName(name);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String name) {
        return symbols.lookupQualifiedName(name);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String name) {
        return symbols.lookupMembers(name);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String owner, String name) {
        return symbols.lookupMember(owner, name);
    }
}
