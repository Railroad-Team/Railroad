package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class JavaReferenceGraphRolesTest {
    @Test
    public void arrayElementWritesReadTheArrayAndIndexVariables() {
        String source = "class A { void run(int[] array, int index) { array[0] = 1; array[index] += 2; ++array[index]; } }";
        SemanticIndexShard shard = extract(source);
        SymbolDescriptor array = declaration(shard, "array");
        SymbolDescriptor index = declaration(shard, "index");
        assertRoles(shard, array.id(), source.indexOf("array[0]"), Set.of(ReferenceKindId.READ));
        assertRoles(shard, array.id(), source.indexOf("array[index]"), Set.of(ReferenceKindId.READ));
        assertRoles(shard, index.id(), source.indexOf("index]"), Set.of(ReferenceKindId.READ));
        assertRoles(shard, array.id(), source.lastIndexOf("array[index]"), Set.of(ReferenceKindId.READ));
        assertRoles(shard, index.id(), source.lastIndexOf("index]"), Set.of(ReferenceKindId.READ));
    }

    @Test
    public void fieldUpdatesReadReceiverAndWriteOnlySelectedField() {
        String source = "class Example { int value; void run(Example receiver) { receiver.value = 1; ++receiver.value; } }";
        SemanticIndexShard shard = extract(source);
        SymbolDescriptor receiver = declaration(shard, "receiver");
        SymbolDescriptor value = declaration(shard, "value");
        assertRoles(shard, receiver.id(), source.indexOf("receiver.value"), Set.of(ReferenceKindId.READ));
        assertRoles(shard, value.id(), source.indexOf("value ="), Set.of(ReferenceKindId.WRITE));
        assertRoles(shard, receiver.id(), source.lastIndexOf("receiver.value"), Set.of(ReferenceKindId.READ));
        assertRoles(shard, value.id(), source.lastIndexOf("value;"),
            Set.of(ReferenceKindId.READ, ReferenceKindId.WRITE));
    }

    @Test
    public void occurrencesAreUniqueByTargetRoleAndRange() {
        SemanticIndexShard shard = extract("class A { int value; int read() { return this.value + value; } }");
        List<List<Object>> keys = shard.references().stream()
            .map(reference -> List.of(reference.target(), reference.kind(),
                reference.location().range().start, reference.location().range().end))
            .toList();
        assertEquals(keys.size(), new HashSet<>(keys).size(), "Nested resolved nodes must not duplicate occurrences");
    }

    @Test
    public void sameNamedFieldAndReceiverHaveDistinctRangesAndRoles() {
        String source = "class Example { int value; void run(Example value) { value.value = 1; } }";
        SemanticIndexShard shard = extract(source);
        SymbolDescriptor field = shard.declarations().stream().filter(symbol -> symbol.kind().id().equals("java:field"))
            .findFirst()
            .orElseThrow();
        SymbolDescriptor receiver = shard.declarations().stream()
            .filter(symbol -> symbol.kind().id().equals("java:parameter"))
            .findFirst().orElseThrow();
        assertRoles(shard, field.id(), source.indexOf("value ="), Set.of(ReferenceKindId.WRITE));
        assertRoles(shard, receiver.id(), source.indexOf("value.value"), Set.of(ReferenceKindId.READ));
    }

    @Test
    public void localInitializerCallsBelongToEnclosingCallable() {
        SemanticIndexShard shard = extract("class A { int value() { return 1; } void run() { int local = value(); } }");
        SymbolDescriptor run = declaration(shard, "run");
        SymbolDescriptor value = declaration(shard, "value");
        assertTrue(shard.edges().contains(new SymbolEdge(run.id(), value.id(), EdgeKindId.CALL)));
        SymbolDescriptor local = declaration(shard, "local");
        assertFalse(shard.edges().stream().anyMatch(edge -> edge.source().equals(local.id())));
    }

    @Test
    public void extractionCancellationDoesNotReturnPartialShard() {
        var model = JavaSemanticAnalyzer.analyzeFacts("class A { int value; int read() { return value; } }",
            JavaProjectSemanticIndex.empty());
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> JavaSemanticGraphExtractor.extract(model, () -> {
            if (checks.incrementAndGet() == 5)
                throw new CancellationException();
        }));
    }

    private static SemanticIndexShard extract(String source) {
        JavaProjectSemanticIndex index = JavaProjectSemanticIndex.builder()
            .putFile(new JavaProjectSemanticExtractor().extract(Path.of("A.java"), source)).build();
        return JavaSemanticGraphExtractor.extract(JavaSemanticAnalyzer.analyzeFacts(source, index), () -> {
        });
    }

    private static SymbolDescriptor declaration(SemanticIndexShard shard, String name) {
        return shard.declarations().stream().filter(symbol -> symbol.simpleName().equals(name)).findFirst()
            .orElseThrow();
    }

    private static void assertRoles(
        SemanticIndexShard shard,
        SymbolId target,
        int offset,
        Set<ReferenceKindId> expected
    ) {
        Set<ReferenceKindId> actual = new HashSet<>();
        for (ReferenceOccurrence reference : shard.references()) {
            if (reference.target().equals(target) && reference.location().range().start == offset) {
                actual.add(reference.kind());
            }
        }

        assertEquals(expected, actual, "Roles at offset " + offset);
    }
}
