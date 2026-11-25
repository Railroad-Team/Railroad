package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor;
import static org.junit.jupiter.api.Assertions.*;

public class JavaWorkspaceGraphTest {
    @Test
    public void capturedNamedHierarchyProducesSubtypeAndOverrideEdges() {
        Path root = Path.of("captured-hierarchy").toAbsolutePath().normalize();
        var uri = DocumentUri.fromPath(root.resolve("Hierarchy.java"));
        var document = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            """
                class Value {}
                interface Contract<T> { void work(T value); }
                class Base implements Contract<Value> { public void work(Value value) {} }
                class Child extends Base { public void work(Value value) {} }
                class Control { Object value = new Base() {}; }
                """, StandardCharsets.UTF_8);
        var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
            Map.of("module.main.sources", root + ":generated=false"));
        var workspace = new WorkspaceSnapshot("hierarchy", Map.of(document.id(), document), configuration, 0);
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> JavaProjectSemanticIndex.empty())) {
            WorkspaceSymbolGraph graph = provider.capture(workspace).resolvedGraph();
            var base = JavaSymbolIds.type("Base");
            var child = JavaSymbolIds.type("Child");
            var contract = JavaSymbolIds.type("Contract");
            assertTrue(graph.outgoingEdges(base).stream().anyMatch(edge -> edge.kind().equals(EdgeKindId.SUBTYPE)
                && edge.target().equals(contract)));
            assertTrue(graph.outgoingEdges(child).stream().anyMatch(edge -> edge.kind().equals(EdgeKindId.SUBTYPE)
                && edge.target().equals(base)));
            var childMethod = JavaSymbolIds.method(child, "work", List.of("Value"));
            var baseMethod = JavaSymbolIds.method(base, "work", List.of("Value"));
            var contractMethod = JavaSymbolIds.method(contract, "work", List.of("java.lang.Object"));
            assertTrue(
                graph.outgoingEdges(childMethod).stream().anyMatch(edge -> edge.kind().equals(EdgeKindId.OVERRIDE)
                    && edge.target().equals(baseMethod)));
            assertTrue(graph.outgoingEdges(baseMethod).stream().anyMatch(edge -> edge.kind().equals(EdgeKindId.OVERRIDE)
                && edge.target().equals(contractMethod)));
            assertTrue(graph.incomingEdges(base).stream().anyMatch(edge -> edge.kind().equals(EdgeKindId.SUBTYPE)
                && edge.source().value().contains("/anonymous/")));
        }
    }

    @Test
    public void wildcardImportedCrossFileCallsUseThePublishedDeclarationIdentity() {
        Path root = Path.of("captured-graph").toAbsolutePath().normalize();
        var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
            Map.of("module.main.sources", root + ":generated=false"));
        Map<DocumentId, DocumentSnapshot> documents = new HashMap<>();
        for (Map.Entry<String, String> source : Map.of("p/Value.java", "package p; public class Value {}",
            "p/Library.java", "package p; public class Library { public void accept(Value value) {} }",
            "q/Use.java",
            "package q; import p.*; class Use { void run(Library library, Value value) { library.accept(value); } }")
            .entrySet()) {
            var uri = DocumentUri.fromPath(root.resolve(source.getKey()));
            var snapshot = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
                DocumentOrigin.original(uri), source.getValue(), StandardCharsets.UTF_8);
            documents.put(snapshot.id(), snapshot);
        }

        var workspace = new WorkspaceSnapshot("test", documents, configuration, 0);
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> JavaProjectSemanticIndex.empty())) {
            JavaWorkspaceAnalysis captured = provider.capture(workspace);
            SymbolDescriptor method = captured.sourceIndex().lookupMember("p.Library", "accept").getFirst()
                .descriptor();
            assertNotNull(method);
            WorkspaceSymbolGraph graph = captured.resolvedGraph();
            assertTrue(graph.referencesTo(method.id()).stream()
                .anyMatch(reference -> reference.kind().equals(ReferenceKindId.CALL)));
            assertFalse(graph.symbols(method.id()).isEmpty());
        }
    }

    @Test
    public void repeatedQueriesReuseCompletedCapturedGraph() {
        JavaWorkspaceAnalysis analysis = analysis();
        WorkspaceSymbolGraph first = analysis.resolvedGraph();
        assertSame(first, analysis.resolvedGraph());
        assertSame(analysis.graph(), analysis.graph());
        assertTrue(first.shards().values().stream().flatMap(shard -> shard.references().stream())
            .anyMatch(reference -> reference.kind().equals(ReferenceKindId.CALL)));
        assertTrue(analysis.graph().shards().values().stream().flatMap(shard -> shard.references().stream())
            .noneMatch(reference -> reference.kind().equals(ReferenceKindId.CALL)));
    }

    @Test
    public void cancellationDiscardsPartialConstructionAndStillChecksCachedQueries() {
        JavaWorkspaceAnalysis analysis = analysis();
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> analysis.resolvedGraph(() -> {
            if (checks.incrementAndGet() == 12)
                throw new CancellationException();
        }));

        WorkspaceSymbolGraph result = analysis.resolvedGraph();
        assertTrue(result.shards().values().stream().flatMap(shard -> shard.edges().stream())
            .anyMatch(edge -> edge.kind().equals(EdgeKindId.CALL)));
        assertSame(result, analysis.resolvedGraph());
        assertThrows(CancellationException.class, () -> analysis.resolvedGraph(() -> {
            throw new CancellationException();
        }));
    }

    @Test
    public void concurrentQueriesShareOneSuccessfulConstruction() throws Exception {
        JavaWorkspaceAnalysis analysis = analysis();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            Callable<WorkspaceSymbolGraph> query = () -> {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                return analysis.resolvedGraph();
            };

            Future<WorkspaceSymbolGraph> first = executor.submit(query);
            Future<WorkspaceSymbolGraph> second = executor.submit(query);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            assertSame(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }

    private static JavaWorkspaceAnalysis analysis() {
        Path path = Path.of("Captured.java").toAbsolutePath().normalize();
        String text = "class Captured { int value() { return 1; } int run() { return value(); } }";
        JavaProjectSemanticIndex sources = JavaProjectSemanticIndex.builder()
            .putFile(new JavaProjectSemanticExtractor().extract(path, text)).build();
        return new JavaWorkspaceAnalysis(sources, sources, Map.of(path, text), List.of(),
            Path.of(System.getProperty("java.home")));
    }
}
