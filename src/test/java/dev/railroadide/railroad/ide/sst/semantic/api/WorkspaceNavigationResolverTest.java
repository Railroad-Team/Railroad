package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceNavigationResolverTest {
    private static final SymbolId TYPE = new SymbolId("test:Type");
    private static final SymbolId CALL = new SymbolId("test:constructor");
    private final TextDocumentSnapshot source = document("Source.java", "new Type();");
    private final TextDocumentSnapshot destination = document("Type.java", "class Type {}");

    @TestFactory
    public Stream<DynamicTest> resolvesEveryCharacterInHalfOpenReference() {
        return Stream.of(4, 5, 6, 7)
            .map(value -> DynamicTest.dynamicTest(String.valueOf(value),
                () -> resolvesEveryCharacterInHalfOpenReference(value)));
    }

    private void resolvesEveryCharacterInHalfOpenReference(int offset) {
        assertEquals(TYPE, SemanticNavigationResolver.symbolAt(references(ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8)),
            source, offset).orElseThrow());
    }

    @TestFactory
    public Stream<DynamicTest> doesNotResolveOutsideReference() {
        return Stream.of(-1, 0, 3, 8, 10, 11, 100)
            .map(value -> DynamicTest.dynamicTest(String.valueOf(value), () -> doesNotResolveOutsideReference(value)));
    }

    private void doesNotResolveOutsideReference(int offset) {
        assertTrue(SemanticNavigationResolver.symbolAt(references(ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8)),
            source, offset).isEmpty());
    }

    @Test
    public void rejectsMissingShardAndMismatchedRevisionOrUri() {
        WorkspaceSymbolGraph graph = references(ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8));
        assertTrue(SemanticNavigationResolver.symbolAt(WorkspaceSymbolGraph.of(List.of()), source, 4).isEmpty());
        var changed = new TextDocumentSnapshot(source.id(), source.uri(), source.version().next(), "java",
            source.origin(), source.text(), StandardCharsets.UTF_8);
        assertTrue(SemanticNavigationResolver.symbolAt(graph, changed, 4).isEmpty());
        var renamed = new TextDocumentSnapshot(source.id(), destination.uri(), source.version(), "java",
            destination.origin(), source.text(), StandardCharsets.UTF_8);
        assertTrue(SemanticNavigationResolver.symbolAt(graph, renamed, 4).isEmpty());
    }

    @Test
    public void deduplicatesDifferentReferenceRolesForOneSymbol() {
        WorkspaceSymbolGraph graph = references(ref(TYPE, ReferenceKindId.READ, 4, 8),
            ref(TYPE, ReferenceKindId.WRITE, 4, 8));
        assertEquals(TYPE, SemanticNavigationResolver.symbolAt(graph, source, 4).orElseThrow());
    }

    @Test
    public void constructorCallWinsOverTypeUseRegardlessOfOrdering() {
        ReferenceOccurrence type = ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8);
        ReferenceOccurrence call = ref(CALL, ReferenceKindId.CALL, 4, 8);
        for (WorkspaceSymbolGraph graph : List.of(references(type, call), references(call, type))) {
            assertEquals(CALL, SemanticNavigationResolver.symbolAt(graph, source, 5).orElseThrow());
        }
    }

    @Test
    public void overlappingButDifferentRangesRemainAmbiguous() {
        WorkspaceSymbolGraph graph = references(ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8),
            ref(CALL, ReferenceKindId.CALL, 0, 10));
        assertTrue(SemanticNavigationResolver.symbolAt(graph, source, 5).isEmpty());
    }

    @Test
    public void multipleCallsAreNotSilentlyResolvedToTheFirst() {
        WorkspaceSymbolGraph graph = references(ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8),
            ref(CALL, ReferenceKindId.CALL, 4, 8),
            ref(new SymbolId("test:otherConstructor"), ReferenceKindId.CALL, 4, 8));
        assertTrue(SemanticNavigationResolver.symbolAt(graph, source, 5).isEmpty());
    }

    @Test
    public void callDoesNotSuppressOtherReferenceKinds() {
        WorkspaceSymbolGraph graph = references(ref(TYPE, ReferenceKindId.READ, 4, 8),
            ref(CALL, ReferenceKindId.CALL, 4, 8));
        assertTrue(SemanticNavigationResolver.symbolAt(graph, source, 5).isEmpty());
    }

    @Test
    public void definitionPrefersSelectionAndRetainsCapturedDestination() {
        var selection = Location.TextLocation.from(destination, 6, 10);
        WorkspaceSymbolGraph graph = definitions(location(destination, selection, true));
        List<DefinitionTarget> targets = SemanticNavigationResolver.definitionsOf(graph, workspace(destination), TYPE);
        assertEquals(1, targets.size());
        DefinitionTarget target = targets.getFirst();
        assertSame(destination, target.snapshot());
        assertEquals(selection, target.location());
    }

    @Test
    public void definitionFallsBackToDeclarationWhenSelectionIsAbsent() {
        SymbolLocation location = location(destination, null, true);
        DefinitionTarget target = SemanticNavigationResolver
            .definitionsOf(definitions(location), workspace(destination), TYPE)
            .getFirst();
        assertEquals(location.declaration(), target.location());
    }

    @Test
    public void duplicateLocationsAreOneTargetAndDistinctLocationsAreAllReturnedInRankOrder() {
        SymbolLocation first = location(destination, Location.TextLocation.from(destination, 6, 10), true);
        assertEquals(1, SemanticNavigationResolver.definitionsOf(definitions(first, first), workspace(destination),
            TYPE).size());
        SymbolLocation second = location(destination, Location.TextLocation.from(destination, 0, 5), true);
        List<DefinitionTarget> targets = SemanticNavigationResolver.definitionsOf(definitions(first, second),
            workspace(destination),
            TYPE);
        assertEquals(List.of(0, 6), targets.stream().map(target -> target.location().range().start).toList());
    }

    @Test
    public void missingSymbolAndMissingDestinationReturnEmpty() {
        WorkspaceSymbolGraph graph = definitions(location(destination, null, true));
        assertTrue(SemanticNavigationResolver.definitionsOf(graph, workspace(destination), CALL).isEmpty());
        assertTrue(SemanticNavigationResolver.definitionsOf(graph, workspace(source), TYPE).isEmpty());
    }

    @TestFactory
    public Stream<DynamicTest> invalidOrUnsupportedDestinationsAreSkipped() {
        return Stream.of("stale", "uri", "bounds", "coordinates", "notNavigable", "virtual", "binary", "noRange")
            .map(value -> DynamicTest.dynamicTest(String.valueOf(value),
                () -> invalidOrUnsupportedDestinationsAreSkipped(value)));
    }

    private void invalidOrUnsupportedDestinationsAreSkipped(String kind) {
        DocumentUri uri = kind.equals("virtual") ? DocumentUri.parse("untitled:Type.java") : destination.uri();
        var snapshot = new TextDocumentSnapshot(destination.id(), uri, destination.version(), "java",
            DocumentOrigin.original(uri), destination.text(), StandardCharsets.UTF_8);
        Location<?> declaration = switch (kind) {
            case "bounds" -> new Location.TextLocation(snapshot.id(), snapshot.version(), new TextRange(0, 99), 0, 0);
            case "coordinates" ->
                new Location.TextLocation(snapshot.id(), snapshot.version(), new TextRange(0, 5), 9, 0);
            case "binary" -> new Location.BinaryLocation(snapshot.id(), snapshot.version(), new BinaryRange(0, 5));
            case "noRange" -> null;
            default -> Location.TextLocation.from(snapshot, 0, snapshot.text().length());
        };

        var location = new SymbolLocation(uri, snapshot.origin(), declaration, null, !kind.equals("notNavigable"));
        TextDocumentSnapshot captured = switch (kind) {
            case "stale" -> new TextDocumentSnapshot(snapshot.id(), uri, snapshot.version().next(), "java",
                snapshot.origin(), snapshot.text(), StandardCharsets.UTF_8);
            case "uri" -> new TextDocumentSnapshot(snapshot.id(), source.uri(), snapshot.version(), "java",
                source.origin(), snapshot.text(), StandardCharsets.UTF_8);
            default -> snapshot;
        };

        assertTrue(SemanticNavigationResolver.definitionsOf(definitions(location), workspace(captured), TYPE)
            .isEmpty());
    }

    @Test
    public void invalidAlternativeDoesNotHideAValidDefinition() {
        SymbolLocation valid = location(destination, null, true);
        SymbolLocation ignored = location(destination, Location.TextLocation.from(destination, 6, 10), false);
        assertEquals(List.of(valid.declaration()), SemanticNavigationResolver.definitionsOf(definitions(ignored, valid),
            workspace(destination), TYPE).stream().map(DefinitionTarget::location).toList());
    }

    @Test
    public void definitionAtComposesReferenceAndCrossFileDeclarationLookup() {
        WorkspaceSymbolGraph declarationGraph = definitions(
            location(destination, Location.TextLocation.from(destination, 6, 10), true));
        List<SemanticIndexShard> shards = new ArrayList<>(declarationGraph.shards().values());
        shards.addAll(references(ref(TYPE, ReferenceKindId.TYPE_USE, 4, 8)).shards().values());
        var graph = WorkspaceSymbolGraph.of(shards);
        DefinitionTarget target = SemanticNavigationResolver
            .definitionAt(graph, workspace(source, destination), source, 5)
            .getFirst();
        assertEquals(destination.id(), target.location().documentId());
        assertEquals(6, target.location().range().start);
        assertTrue(SemanticNavigationResolver.definitionAt(graph, workspace(source, destination), source, 8).isEmpty());
    }

    private ReferenceOccurrence ref(SymbolId id, ReferenceKindId kind, int start, int end) {
        return new ReferenceOccurrence(id, kind, Location.TextLocation.from(source, start, end), null);
    }

    @Test
    public void binaryDestinationSnapshotCannotSatisfyATextDefinition() {
        WorkspaceSymbolGraph graph = definitions(location(destination, null, true));
        var binary = new BinaryDocumentSnapshot(destination.id(), destination.uri(), destination.version(), "binary",
            new byte[20]);
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        store.publishBacking(binary);
        assertTrue(SemanticNavigationResolver.definitionsOf(graph, store.capture(), TYPE).isEmpty());
    }

    @Test
    public void referenceOffsetsUseUtf16AcrossSurrogatePairsAndCrLf() {
        TextDocumentSnapshot snapshot = document("Unicode.java", "// \uD83D\uDE00\r\nType");
        int start = snapshot.text().indexOf("Type");
        var occurrence = new ReferenceOccurrence(TYPE, ReferenceKindId.TYPE_USE,
            Location.TextLocation.from(snapshot, start, start + 4), null);
        var graph = WorkspaceSymbolGraph.of(List.of(new SemanticIndexShard(snapshot.id(), snapshot.version(),
            snapshot.uri(), List.of(), List.of(occurrence), List.of())));
        assertEquals(TYPE, SemanticNavigationResolver.symbolAt(graph, snapshot, start).orElseThrow());
        assertTrue(SemanticNavigationResolver.symbolAt(graph, snapshot, start - 1).isEmpty());
        assertTrue(SemanticNavigationResolver.symbolAt(graph, snapshot, start + 4).isEmpty());
    }

    private WorkspaceSymbolGraph references(ReferenceOccurrence... references) {
        return WorkspaceSymbolGraph.of(List.of(new SemanticIndexShard(source.id(), source.version(), source.uri(),
            List.of(), List.of(references), List.of())));
    }

    private WorkspaceSymbolGraph definitions(SymbolLocation... locations) {
        var descriptor = new SymbolDescriptor(TYPE, new SymbolKindId("test:type"), "Type", null, null, null, null,
            SymbolMetadata.EMPTY, List.of(locations));
        TextDocumentSnapshot owner = document("Index.java", "");
        return WorkspaceSymbolGraph.of(List.of(new SemanticIndexShard(owner.id(), owner.version(), owner.uri(),
            List.of(descriptor), List.of(), List.of())));
    }

    private static SymbolLocation location(
        TextDocumentSnapshot snapshot,
        Location.TextLocation selection,
        boolean navigable
    ) {
        return new SymbolLocation(snapshot.uri(), snapshot.origin(),
            Location.TextLocation.from(snapshot, 0, snapshot.text().length()), selection, navigable);
    }

    private static TextDocumentSnapshot document(String name, String text) {
        var uri = DocumentUri.fromPath(Path.of(name));
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
    }

    private static WorkspaceSnapshot workspace(TextDocumentSnapshot... documents) {
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        for (TextDocumentSnapshot document : documents) {
            store.publishBacking(document);
        }

        return store.capture();
    }
}
