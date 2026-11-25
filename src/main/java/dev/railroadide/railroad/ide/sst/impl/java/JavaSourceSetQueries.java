package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Resolves references from dependent source sets with each caller's own compile inputs.
 */
public final class JavaSourceSetQueries {
    private final FeatureRequestContext request;
    private final Function<FeatureRequestContext, JavaWorkspaceAnalysis> capture;
    private final JavaWorkspaceAnalysis origin;
    private final JavaSourceSets sets;
    private final JavaSourceSets.Scope scope;
    private final boolean libraryView;

    public JavaSourceSetQueries(
        FeatureRequestContext request,
        Function<FeatureRequestContext, JavaWorkspaceAnalysis> capture
    ) {
        this.request = request;
        this.capture = capture;
        this.origin = capture.apply(request);
        this.sets = JavaSourceSets.of(request.workspaceSnapshot().configuration());
        this.scope = owner(request.documentSnapshot());
        ProvenanceKind provenance = request.documentSnapshot().origin().provenance();
        this.libraryView = !sets.isEmpty() && (provenance == ProvenanceKind.ATTACHED_SOURCE
            || provenance == ProvenanceKind.DECOMPILED);
    }

    private JavaSourceSets.Scope owner(DocumentSnapshot document) {
        return document.origin().provenance() == ProvenanceKind.ATTACHED_SOURCE
            ? null
            : document.uri().filePath().map(sets::owner).orElse(null);
    }

    public UsageSearch usages(UsageQuery query, UsageSink sink) {
        WorkspaceSnapshot workspace = request.workspaceSnapshot();
        DocumentSnapshot document = request.documentSnapshot();
        if (scope == null && !libraryView)
            return origin.usages(workspace, document, query, sink, request::checkActive);

        int[] searched = {0};

        UsageSearch first = origin.usages(workspace, document, query, (batch, count, total) -> {
            searched[0] = count;
            sink.accept(batch, count, total);
        }, request::checkActive, path -> sets.owner(path) == scope);
        if (first == null)
            return null;

        List<Location<?>> results = new ArrayList<>(first.usages());
        var target = new UsageQuery.OfSymbol(first.symbol());
        visitScopes(Set.of(first.symbol()), false, (candidate, analysis) -> {
            int previous = searched[0];
            UsageSearch additional = analysis.usages(workspace, document, target, (batch, count, total) -> {
                searched[0] = previous + count;
                sink.accept(batch, searched[0], previous + total);
            }, request::checkActive, path -> sets.owner(path) == candidate);
            if (additional != null) {
                results.addAll(additional.usages());
            }
        });

        results.sort(Comparator
            .comparing(
                (Location<?> location) -> workspace.document(location.documentId()).orElseThrow().uri().toString())
            .thenComparingInt(l -> l.range().start));
        return new UsageSearch(first.symbol(), first.displayName(), results);
    }

    public WorkspaceSymbolGraph graph(SymbolId target, boolean rename) {
        WorkspaceSymbolGraph base = rename
            ? origin.renameGraph(request::checkActive)
            : origin.resolvedGraph(request::checkActive);
        if ((scope == null && !libraryView) || target == null)
            return base;

        Set<SymbolId> anchors = new HashSet<>();
        anchors.add(target);
        if (rename) {
            Deque<SymbolId> pending = new ArrayDeque<>(anchors);
            while (!pending.isEmpty()) {
                for (SymbolEdge edge : base.outgoingEdges(pending.removeFirst())) {
                    if (edge.kind().equals(EdgeKindId.OVERRIDE) && anchors.add(edge.target())) {
                        pending.add(edge.target());
                    }
                }
            }
        }

        Map<DocumentId, SemanticIndexShard> shards = new LinkedHashMap<>();
        for (SemanticIndexShard shard : base.shards().values()) {
            request.checkActive();
            Optional<DocumentSnapshot> source = request.workspaceSnapshot().document(shard.documentId());
            if (source.isEmpty() || owner(source.get()) == scope) {
                shards.put(shard.documentId(), shard);
            }
        }

        visitScopes(anchors, true, (candidate, analysis) -> {
            WorkspaceSymbolGraph graph = rename
                ? analysis.renameGraph(request::checkActive)
                : analysis.resolvedGraph(request::checkActive);
            for (SemanticIndexShard shard : graph.shards().values()) {
                request.checkActive();
                Optional<DocumentSnapshot> document = request.workspaceSnapshot().document(shard.documentId());
                if (document.isEmpty()) {
                    shards.putIfAbsent(shard.documentId(), shard);
                } else if (owner(document.get()) == candidate) {
                    shards.put(shard.documentId(), shard);
                }
            }
        });

        return WorkspaceSymbolGraph.of(shards.values(), request::checkActive);
    }

    private void visitScopes(
        Set<SymbolId> targets,
        boolean includeDependencies,
        BiConsumer<JavaSourceSets.Scope, JavaWorkspaceAnalysis> visit
    ) {
        Map<String, SemanticIndexShard> declarations = new HashMap<>();
        for (SymbolId target : targets) {
            String name = JavaSymbolIds.owningTypeName(target);
            if (name == null)
                continue;

            SemanticIndexShard shard = origin.declarationShard(name, request::checkActive);
            if (shard != null) {
                declarations.put(name, shard);
            }
        }

        if (declarations.isEmpty())
            return;

        Set<JavaSourceSets.Scope> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(scope);
        WorkspaceSnapshot workspace = request.workspaceSnapshot();
        for (DocumentSnapshot document : workspace.documents().values()) {
            request.checkActive();
            if (!(document instanceof TextDocumentSnapshot) || !"java".equals(document.languageId()))
                continue;

            JavaSourceSets.Scope candidate = owner(document);
            if (candidate == null || !visited.add(candidate))
                continue;

            boolean reachable = declarations.values().stream().anyMatch(shard -> shard.uri().filePath()
                .filter(path -> sets.owner(path) != null).map(path -> sets.visible(candidate, path)).orElse(true));
            boolean dependency = includeDependencies && scope != null && document.uri().filePath()
                .filter(path -> sets.visible(scope, path)).isPresent();
            if (!reachable && !dependency)
                continue;

            var context = new FeatureRequestContext(workspace, document, request.cancellationToken(),
                request.deadline());
            JavaWorkspaceAnalysis analysis = capture.apply(context);
            if (dependency) {
                visit.accept(candidate, analysis);
                continue;
            }

            for (Map.Entry<String, SemanticIndexShard> declaration : declarations.entrySet()) {
                SemanticIndexShard actual = analysis.declarationShard(declaration.getKey(), request::checkActive);
                if (actual == null)
                    continue;

                DocumentUri expected = declaration.getValue().uri();
                boolean sameView = libraryView && expected.equals(request.documentSnapshot().uri())
                    && analysis.topLevelBinaryLocation(declaration.getKey())
                        .map(binary -> request.documentSnapshot().origin().sources()
                            .contains(new OriginReference(binary)))
                        .orElse(false);
                if (actual.uri().equals(expected) || sameView) {
                    visit.accept(candidate, analysis);
                    break;
                }
            }
        }
    }
}
