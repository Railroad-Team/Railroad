package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.internal.SemanticGraphCodec;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceSymbolGraphTest {
    private static final SymbolId CALLER = new SymbolId("test:caller");
    private static final SymbolId TARGET = new SymbolId("test:target(int)");
    private static final SymbolId OVERLOAD = new SymbolId("test:target(String)");

    @Test
    public void movingOwnedDocumentRemovesOldPathAndRetainsItsGraphIdentity() {
        SemanticIndexShard original = shard(DocumentId.create(), 0, TARGET);
        Path oldPath = Path.of("old", "A.java").toAbsolutePath().normalize();
        Path newPath = Path.of("new", "A.java").toAbsolutePath().normalize();
        var first = new JavaProjectSemanticIndex.SourceFileIndex(oldPath, null, List.of(), List.of(), null, original);
        var moved = new SemanticIndexShard(original.documentId(), original.documentVersion(),
            DocumentUri.fromPath(newPath),
            original.declarations(), original.references(), original.edges());
        var next = new JavaProjectSemanticIndex.SourceFileIndex(newPath, null, List.of(), List.of(), null, moved);
        JavaProjectSemanticIndex captured = JavaProjectSemanticIndex.builder().putFile(first).build();
        JavaProjectSemanticIndex result = captured.withFile(next);
        assertEquals(Set.of(newPath), result.files().keySet());
        assertEquals(List.of(original.references().getFirst()), result.graph().referencesTo(TARGET));
        assertEquals(moved.uri(), result.graph().shards().get(original.documentId()).uri());
        assertTrue(captured.containsFile(oldPath));
    }

    @Test
    public void replacesAndDeletesOnlyOwnedPostingsAndPreservesCapturedAnswers() {
        SemanticIndexShard first = shard(DocumentId.create(), 0, TARGET);
        SemanticIndexShard unrelated = shard(DocumentId.create(), 0, OVERLOAD);
        var captured = WorkspaceSymbolGraph.of(List.of(first, unrelated));
        WorkspaceSymbolGraph updated = captured.replaceShard(shard(first.documentId(), 1, OVERLOAD));
        assertEquals(1, captured.referencesTo(TARGET).size());
        assertTrue(updated.referencesTo(TARGET).isEmpty());
        assertEquals(2, updated.referencesTo(OVERLOAD).size());
        assertSame(unrelated, updated.shards().get(unrelated.documentId()));
        WorkspaceSymbolGraph deleted = updated.removeShard(first.documentId());
        assertEquals(List.of(unrelated.references().getFirst()), deleted.referencesTo(OVERLOAD));
        assertEquals(1, deleted.incomingEdges(OVERLOAD).size());
        assertEquals(1, deleted.outgoingEdges(CALLER).size());
        assertThrows(UnsupportedOperationException.class, () -> deleted.referencesTo(OVERLOAD).clear());
    }

    @Test
    public void overlayShadowsDiskAndClosingRevealsLatestDiskRevision() {
        SemanticIndexShard disk = shard(DocumentId.create(), 0, TARGET);
        WorkspaceSymbolGraph graph = WorkspaceSymbolGraph.of(List.of(disk))
            .withOverlay(shard(disk.documentId(), 2, OVERLOAD));
        SemanticIndexShard diskUpdate = shard(disk.documentId(), 1, CALLER);
        graph = graph.replaceShard(diskUpdate);
        assertEquals(1, graph.referencesTo(OVERLOAD).size());
        assertTrue(graph.referencesTo(CALLER).isEmpty());
        WorkspaceSymbolGraph closed = graph.withoutOverlay(disk.documentId());
        assertTrue(closed.referencesTo(OVERLOAD).isEmpty());
        assertEquals(1, closed.referencesTo(CALLER).size());
        assertTrue(graph.removeShard(disk.documentId()).shards().isEmpty());
    }

    @Test
    public void rejectsStaleUpdatesAndMismatchedReferenceVersions() {
        SemanticIndexShard first = shard(DocumentId.create(), 2, TARGET);
        var graph = WorkspaceSymbolGraph.of(List.of(first));
        assertThrows(IllegalArgumentException.class,
            () -> graph.replaceShard(shard(first.documentId(), 1, TARGET)));
        assertThrows(IllegalArgumentException.class,
            () -> graph.withOverlay(shard(first.documentId(), 1, TARGET)));
        assertThrows(IllegalArgumentException.class, () -> new SemanticIndexShard(first.documentId(),
            new DocumentVersion(3), first.uri(), List.of(), first.references(), List.of()));
        var staleLocation = new Location.TextLocation(first.documentId(), new DocumentVersion(1),
            new TextRange(0, 1), 0, 0);
        var staleDeclaration = new SymbolDescriptor(TARGET, new SymbolKindId("test:method"), "target", null,
            null, null, null, SymbolMetadata.EMPTY, List.of(new SymbolLocation(first.uri(),
                DocumentOrigin.original(first.uri()), staleLocation, staleLocation, true)));
        assertThrows(IllegalArgumentException.class, () -> new SemanticIndexShard(first.documentId(),
            first.documentVersion(), first.uri(), List.of(staleDeclaration), List.of(), List.of()));
    }

    @Test
    public void validatesFactsAgainstExactWorkspaceRevisionAndLocation() {
        SemanticIndexShard first = shard(DocumentId.create(), 2, TARGET);
        var text = new TextDocumentSnapshot(first.documentId(), first.uri(), first.documentVersion(),
            "test", "abc", StandardCharsets.UTF_8);
        var workspace = new WorkspaceSnapshot("test", Map.of(text.id(), text),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        assertDoesNotThrow(() -> first.validateAgainst(workspace));
        var changed = new TextDocumentSnapshot(text.id(), text.uri(), new DocumentVersion(3),
            "test", "xyz", StandardCharsets.UTF_8);
        var later = new WorkspaceSnapshot("test", Map.of(changed.id(), changed), workspace.configuration(), 1);
        assertThrows(IllegalArgumentException.class, () -> first.validateAgainst(later));
    }

    @Test
    public void concurrentUnrelatedReplacementsComposeWithoutLosingChanges() throws Exception {
        AtomicReference<WorkspaceSymbolGraph> graph = new AtomicReference<>(WorkspaceSymbolGraph.empty());
        SemanticIndexShard first = shard(DocumentId.create(), 0, TARGET);
        SemanticIndexShard second = shard(DocumentId.create(), 0, OVERLOAD);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (SemanticIndexShard shard : List.of(first, second)) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        assertTrue(go.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        throw new RuntimeException(exception);
                    }

                    graph.updateAndGet(previous -> previous.replaceShard(shard));
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }

        assertEquals(2, graph.get().shards().size());
        assertEquals(1, graph.get().referencesTo(TARGET).size());
        assertEquals(1, graph.get().referencesTo(OVERLOAD).size());
    }

    @Test
    public void persistsVersionedSymbolsReferencesEdgesAndAlternateLocations() throws Exception {
        SemanticIndexShard shard = shard(DocumentId.create(), 0, TARGET);
        var location = new SymbolLocation(shard.uri(), DocumentOrigin.original(shard.uri()),
            shard.references().getFirst().location(), null, true);
        var declaration = new SymbolDescriptor(CALLER, new SymbolKindId("test:function"), "caller", null,
            null, new SymbolSignature(List.of(), List.of(new SymbolSignature.Parameter("args",
                new TypeReference("String"), true)), new TypeReference("void"), List.of()),
            null, new SymbolMetadata(Set.of("public"), "public", true, List.of(), Map.of("test:flag", "yes")),
            List.of(location, new SymbolLocation(DocumentUri.parse("jar:file:///library.jar!/A.class"),
                new DocumentOrigin(BackingKind.ARCHIVE_ENTRY, ProvenanceKind.ORIGINAL), null, null, true)));
        shard = new SemanticIndexShard(shard.documentId(), shard.documentVersion(), shard.uri(),
            List.of(declaration), shard.references(), shard.edges());
        var bytes = new ByteArrayOutputStream();
        SemanticGraphCodec.write(new DataOutputStream(bytes), shard);
        var loaded = SemanticGraphCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
            SemanticIndexShard.class);
        assertEquals(shard.documentId(), loaded.documentId());
        assertEquals(shard.documentVersion(), loaded.documentVersion());
        assertEquals(shard.uri(), loaded.uri());
        assertEquals(declaration.signature(), loaded.declarations().getFirst().signature());
        assertEquals(declaration.metadata(), loaded.declarations().getFirst().metadata());
        assertEquals(declaration.locations().getLast(), loaded.declarations().getFirst().locations().getLast());
        ReferenceOccurrence reference = WorkspaceSymbolGraph.of(List.of(loaded)).referencesTo(TARGET).getFirst();
        assertEquals(CALLER, reference.enclosingSymbol());
        assertEquals(ReferenceKindId.CALL, reference.kind());
        assertEquals(0, reference.location().range().start);
        assertEquals(1, reference.location().range().end);
        assertEquals(shard.edges(), loaded.edges());
        assertThrows(IOException.class, () -> SemanticGraphCodec.read(
            new DataInputStream(new ByteArrayInputStream(new byte[]{127, -1, -1, -1})), SemanticIndexShard.class));
    }

    private static SemanticIndexShard shard(DocumentId document, long version, SymbolId target) {
        var revision = new DocumentVersion(version);
        var location = new Location.TextLocation(document, revision, new TextRange(0, 1), 0, 0);
        return new SemanticIndexShard(document, revision, DocumentUri.inMemory(document), List.of(),
            List.of(new ReferenceOccurrence(target, ReferenceKindId.CALL, location, CALLER)),
            List.of(new SymbolEdge(CALLER, target, EdgeKindId.CALL)));
    }
}
