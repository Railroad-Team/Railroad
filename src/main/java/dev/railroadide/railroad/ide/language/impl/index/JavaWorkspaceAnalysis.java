package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
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

    public JavaProjectSemanticIndex sourceIndex() {
        return sourceIndex;
    }
    public JavaSymbolIndex symbols() {
        return symbols;
    }
    public Map<Path, String> sourceTexts() {
        return sourceTexts;
    }
    public List<Path> classpath() {
        return classpath;
    }
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
            shards.put(resolved.documentId(), resolved);
        }
        var result = WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(shards.values(), activityCheck),
            activityCheck);
        activityCheck.run();
        return result;
    }

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
