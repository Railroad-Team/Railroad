package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class SemanticNavigationQueryTest {
    private static final SymbolId BASE = new SymbolId("test:Base");
    private static final SymbolId MIDDLE = new SymbolId("test:Middle");
    private static final SymbolId LEAF = new SymbolId("test:Leaf");
    private static final SymbolId CALLER = new SymbolId("test:caller");
    private static final SymbolId FIELD = new SymbolId("test:field");

    private final TextDocumentSnapshot first = document("A.java", "class Middle extends Base { int field; }");
    private final TextDocumentSnapshot second = document("B.java", "class Leaf extends Middle { field = 1; }");

    @Test
    public void implementationsFollowSubtypeAndOverrideEdgesTransitivelyWithoutTheQueriedSymbol() {
        var graph = WorkspaceSymbolGraph.of(List.of(
            shard(first, List.of(declaration(MIDDLE, first, "Middle")), List.of(),
                List.of(new SymbolEdge(MIDDLE, BASE, EdgeKindId.SUBTYPE))),
            shard(second, List.of(declaration(LEAF, second, "Leaf")), List.of(),
                List.of(new SymbolEdge(LEAF, MIDDLE, EdgeKindId.OVERRIDE),
                    new SymbolEdge(BASE, LEAF, EdgeKindId.SUBTYPE),
                    new SymbolEdge(CALLER, BASE, EdgeKindId.CALL)))));
        var targets = SemanticNavigationResolver.implementationsOf(graph, workspace(first, second), BASE, () -> {
        });
        assertEquals(List.of(first.id(), second.id()),
            targets.stream().map(target -> target.location().documentId()).toList());
        assertEquals(List.of("Middle", "Leaf"), targets.stream()
            .map(target -> text(target.snapshot(), target.location()))
            .toList());
    }

    @Test
    public void implementationsWithoutNavigableDeclarationsAreOmitted() {
        var graph = WorkspaceSymbolGraph.of(List.of(shard(first, List.of(), List.of(),
            List.of(new SymbolEdge(MIDDLE, BASE, EdgeKindId.SUBTYPE)))));
        assertTrue(SemanticNavigationResolver.implementationsOf(graph, workspace(first), BASE, () -> {
        }).isEmpty());
    }

    @Test
    public void implementationQueryObservesCancellation() {
        var graph = WorkspaceSymbolGraph.of(List.of(shard(first, List.of(), List.of(),
            List.of(new SymbolEdge(MIDDLE, BASE, EdgeKindId.SUBTYPE)))));
        assertThrows(CancellationException.class, () -> SemanticNavigationResolver.implementationsOf(graph,
            workspace(first), BASE, () -> {
                throw new CancellationException();
            }));
    }

    @Test
    public void usagesExcludeDeclarationsMergeRolesAndAreOrderedByDocumentAndOffset() {
        int declaration = first.text().indexOf("field");
        int write = second.text().indexOf("field");
        var graph = WorkspaceSymbolGraph.of(List.of(
            shard(second, List.of(), List.of(
                reference(FIELD, ReferenceKindId.WRITE, second, write, write + 5),
                reference(FIELD, ReferenceKindId.READ, second, write, write + 5)), List.of()),
            shard(first, List.of(declaration(FIELD, first, "field")), List.of(
                reference(FIELD, ReferenceKindId.DECLARATION, first, declaration, declaration + 5),
                reference(FIELD, ReferenceKindId.DOCUMENTATION, first, 0, 5)), List.of())));
        var usages = SemanticNavigationResolver.usagesOf(graph, workspace(first, second), FIELD);
        assertEquals(List.of(first.id(), second.id()), usages.stream().map(Location::documentId).toList());
        assertEquals(List.of(0, write), usages.stream().map(usage -> usage.range().start).toList());
    }

    @Test
    public void usagesFromAnOutdatedRevisionAreSkippedInsteadOfShifted() {
        int write = second.text().indexOf("field");
        var graph = WorkspaceSymbolGraph.of(List.of(shard(second, List.of(), List.of(
            reference(FIELD, ReferenceKindId.WRITE, second, write, write + 5)), List.of())));
        var edited = new TextDocumentSnapshot(second.id(), second.uri(), second.version().next(), "java",
            second.origin(), "\n" + second.text(), StandardCharsets.UTF_8);
        assertTrue(SemanticNavigationResolver.usagesOf(graph, workspace(edited), FIELD).isEmpty());
        assertTrue(SemanticNavigationResolver.usagesOf(graph, workspace(first), FIELD).isEmpty());
    }

    @Test
    public void originalSourceRanksBeforeDecompiledViewsRegardlessOfAddress() {
        var decompiled = new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(Path.of("A.class.java")),
            DocumentVersion.initial(), "java", new DocumentOrigin(BackingKind.FILE, ProvenanceKind.DECOMPILED),
            "class Leaf {}", StandardCharsets.UTF_8);
        var original = document("Z.java", "class Leaf {}");
        var descriptor = new SymbolDescriptor(LEAF, new SymbolKindId("test:type"), "Leaf", null, null, null, null,
            SymbolMetadata.EMPTY, List.of(view(decompiled, "Leaf"), view(original, "Leaf")));
        var index = document("Index.java", "");
        var graph = WorkspaceSymbolGraph.of(List.of(shard(index, List.of(descriptor), List.of(), List.of())));
        var targets = SemanticNavigationResolver.definitionsOf(graph, workspace(decompiled, original, index), LEAF);
        assertEquals(List.of(original.id(), decompiled.id()),
            targets.stream().map(target -> target.snapshot().id()).toList());
    }

    @Test
    public void displayNamePrefersQualifiedNamesAndFallsBackToTheIdentity() {
        var qualified = new SymbolDescriptor(MIDDLE, new SymbolKindId("test:type"), "Middle", "pkg.Middle", null,
            null, null, SymbolMetadata.EMPTY, List.of());
        var graph = WorkspaceSymbolGraph.of(List.of(shard(first, List.of(qualified, declaration(LEAF, first,
            "Middle")), List.of(), List.of())));
        assertEquals("pkg.Middle", SemanticNavigationResolver.displayName(graph, MIDDLE));
        assertEquals("Middle", SemanticNavigationResolver.displayName(graph, LEAF));
        assertEquals(BASE.value(), SemanticNavigationResolver.displayName(graph, BASE));
    }

    private static SymbolDescriptor declaration(SymbolId id, TextDocumentSnapshot snapshot, String name) {
        return new SymbolDescriptor(id, new SymbolKindId("test:type"), name, null, null, null, null,
            SymbolMetadata.EMPTY, List.of(view(snapshot, name)));
    }

    private static SymbolLocation view(TextDocumentSnapshot snapshot, String name) {
        int start = snapshot.text().indexOf(name);
        return new SymbolLocation(snapshot.uri(), snapshot.origin(),
            Location.TextLocation.from(snapshot, 0, snapshot.text().length()),
            Location.TextLocation.from(snapshot, start, start + name.length()), true);
    }

    private static ReferenceOccurrence reference(
        SymbolId target,
        ReferenceKindId kind,
        TextDocumentSnapshot snapshot,
        int start,
        int end
    ) {
        return new ReferenceOccurrence(target, kind, Location.TextLocation.from(snapshot, start, end), null);
    }

    private static SemanticIndexShard shard(
        TextDocumentSnapshot snapshot,
        List<SymbolDescriptor> declarations,
        List<ReferenceOccurrence> references,
        List<SymbolEdge> edges
    ) {
        return new SemanticIndexShard(snapshot.id(), snapshot.version(), snapshot.uri(), declarations, references,
            edges);
    }

    private static String text(TextDocumentSnapshot snapshot, Location.TextLocation location) {
        return snapshot.text().substring(location.range().start, location.range().end);
    }

    private static TextDocumentSnapshot document(String name, String text) {
        var uri = DocumentUri.fromPath(Path.of(name));
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
    }

    private static WorkspaceSnapshot workspace(TextDocumentSnapshot... documents) {
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        for (var document : documents) {
            store.publishBacking(document);
        }
        return store.capture();
    }
}
