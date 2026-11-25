package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class JavaOverrideGraphExtractorTest {
    @Test
    public void nestedTypesUseTheirDeclaringPackageForPackagePrivateOverrides() {
        SymbolDescriptor base = type("api.Base");
        SymbolDescriptor outer = type("api.Outer");
        SymbolDescriptor otherOuter = type("other.Outer");
        SymbolDescriptor child = nestedType(outer, "Child");
        SymbolDescriptor otherChild = nestedType(otherOuter, "Child");
        SymbolDescriptor inherited = method(base.id(), "run", "package", Set.of());
        SymbolDescriptor implementation = method(child.id(), "run", "public", Set.of());
        SymbolDescriptor inaccessible = method(otherChild.id(), "run", "public", Set.of());
        var id = DocumentId.create();
        var shard = new SemanticIndexShard(id, DocumentVersion.initial(), DocumentUri.inMemory(id),
            List.of(base, outer, otherOuter, child, otherChild, inherited, implementation, inaccessible),
            List.of(), List.of(new SymbolEdge(child.id(), base.id(), EdgeKindId.SUBTYPE),
                new SymbolEdge(otherChild.id(), base.id(), EdgeKindId.SUBTYPE)));
        var graph = WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(List.of(shard), () -> {
        }));

        assertEquals(List.of(new SymbolEdge(implementation.id(), inherited.id(), EdgeKindId.OVERRIDE)),
            graph.outgoingEdges(implementation.id()));
        assertTrue(graph.outgoingEdges(inaccessible.id()).isEmpty());
    }

    @Test
    public void specializesComparableAndGenericChainsWithoutLinkingDifferentGenericArguments() {
        SymbolDescriptor comparable = genericType("api.Comparable", "T");
        SymbolDescriptor comparableString = type("impl.StringOrder");
        SymbolDescriptor compare = genericMethod(comparable.id(), "compareTo", new TypeReference("T"),
            "java.lang.Object");
        SymbolDescriptor compareString = genericMethod(comparableString.id(), "compareTo",
            new TypeReference("java.lang.String"),
            "java.lang.String");
        SymbolDescriptor base = genericType("api.Base", "T");
        SymbolDescriptor middle = genericType("api.Middle", "U");
        SymbolDescriptor child = type("impl.Child");
        SymbolDescriptor wrong = type("impl.Wrong");
        SymbolDescriptor accept = genericMethod(base.id(), "accept", new TypeReference("T"), "java.lang.Object");
        var listString = new TypeReference("java.util.List", List.of(new TypeReference("java.lang.String")), 0, null);
        SymbolDescriptor acceptChild = genericMethod(child.id(), "accept", listString, "java.util.List");
        SymbolDescriptor acceptWrong = genericMethod(wrong.id(), "accept", new TypeReference("java.util.List",
            List.of(new TypeReference("java.lang.Integer")), 0, null), "java.util.List");
        var id = DocumentId.create();
        var shard = new SemanticIndexShard(id, DocumentVersion.initial(), DocumentUri.inMemory(id),
            List.of(comparable, comparableString, compare, compareString, base, middle, child, wrong, accept,
                acceptChild, acceptWrong),
            List.of(), List.of(
                new SymbolEdge(comparableString.id(), comparable.id(), EdgeKindId.SUBTYPE,
                    List.of(new TypeReference("java.lang.String"))),
                new SymbolEdge(middle.id(), base.id(), EdgeKindId.SUBTYPE,
                    List.of(new TypeReference("java.util.List", List.of(new TypeReference("U")), 0, null))),
                new SymbolEdge(child.id(), middle.id(), EdgeKindId.SUBTYPE,
                    List.of(new TypeReference("java.lang.String"))),
                new SymbolEdge(wrong.id(), base.id(), EdgeKindId.SUBTYPE, List.of(listString))));
        var graph = WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(List.of(shard), () -> {
        }));

        assertTrue(graph.outgoingEdges(compareString.id())
            .contains(new SymbolEdge(compareString.id(), compare.id(), EdgeKindId.OVERRIDE)));
        assertTrue(graph.outgoingEdges(acceptChild.id())
            .contains(new SymbolEdge(acceptChild.id(), accept.id(), EdgeKindId.OVERRIDE)));
        assertTrue(graph.outgoingEdges(acceptWrong.id()).isEmpty());
    }

    @Test
    public void findsOverrideAndImplementationWithoutStaticPrivateOrUnrelatedEdges() {
        SymbolDescriptor base = type("api.Base");
        SymbolDescriptor child = type("impl.Child");
        SymbolDescriptor unrelated = type("api.Unrelated");
        SymbolDescriptor inherited = method(base.id(), "run", "public", Set.of());
        SymbolDescriptor implementation = method(child.id(), "run", "public", Set.of());
        SymbolDescriptor other = method(unrelated.id(), "run", "public", Set.of());
        SymbolDescriptor hidden = method(base.id(), "hidden", "private", Set.of());
        SymbolDescriptor hiddenChild = method(child.id(), "hidden", "public", Set.of());
        SymbolDescriptor staticBase = method(base.id(), "utility", "public", Set.of("static"));
        SymbolDescriptor staticChild = method(child.id(), "utility", "public", Set.of("static"));
        SymbolDescriptor packageBase = method(base.id(), "packageOnly", "package", Set.of());
        SymbolDescriptor packageChild = method(child.id(), "packageOnly", "public", Set.of());
        var id = DocumentId.create();
        var shard = new SemanticIndexShard(id, DocumentVersion.initial(), DocumentUri.inMemory(id),
            List.of(base, child, unrelated, inherited, implementation, other, hidden, hiddenChild,
                staticBase, staticChild, packageBase, packageChild),
            List.of(),
            List.of(new SymbolEdge(child.id(), base.id(), EdgeKindId.SUBTYPE)));
        var graph = WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(List.of(shard), () -> {
        }));

        assertEquals(List.of(new SymbolEdge(implementation.id(), inherited.id(), EdgeKindId.OVERRIDE)),
            graph.outgoingEdges(implementation.id()));
        for (SymbolDescriptor absent : List.of(other, hiddenChild, staticChild, packageChild)) {
            assertTrue(graph.outgoingEdges(absent.id()).isEmpty(), absent.simpleName());
        }

        assertEquals(List.of(new SymbolEdge(child.id(), base.id(), EdgeKindId.SUBTYPE)),
            graph.incomingEdges(base.id()));
    }

    private static SymbolDescriptor type(String name) {
        return new SymbolDescriptor(JavaSymbolIds.type(name), new SymbolKindId("java:class"),
            name.substring(name.lastIndexOf('.') + 1), name, null, null, null, SymbolMetadata.EMPTY, List.of());
    }

    private static SymbolDescriptor nestedType(SymbolDescriptor owner, String name) {
        return new SymbolDescriptor(JavaSymbolIds.type(owner.qualifiedName() + "$" + name),
            new SymbolKindId("java:class"), name, owner.qualifiedName() + "." + name, owner.id(),
            null, null, SymbolMetadata.EMPTY, List.of());
    }

    private static SymbolDescriptor genericType(String name, String parameter) {
        SymbolDescriptor type = type(name);
        return new SymbolDescriptor(type.id(), type.kind(), type.simpleName(), type.qualifiedName(), null,
            new SymbolSignature(
                List.of(new SymbolSignature.TypeParameter(parameter, List.of(new TypeReference("java.lang.Object")))),
                List.of(), null, List.of()),
            null, SymbolMetadata.EMPTY, List.of());
    }

    private static SymbolDescriptor genericMethod(SymbolId owner, String name, TypeReference parameter, String erased) {
        return new SymbolDescriptor(JavaSymbolIds.method(owner, name, List.of(erased)), new SymbolKindId("java:method"),
            name, null, owner,
            new SymbolSignature(List.of(), List.of(new SymbolSignature.Parameter("value", parameter, false)),
                new TypeReference("void"), List.of()),
            null,
            new SymbolMetadata(Set.of("public"), "public", false, List.of(), Map.of()), List.of());
    }

    private static SymbolDescriptor method(SymbolId owner, String name, String visibility, Set<String> modifiers) {
        return new SymbolDescriptor(JavaSymbolIds.method(owner, name, List.of("int")),
            new SymbolKindId("java:method"), name, null, owner,
            new SymbolSignature(List.of(),
                List.of(new SymbolSignature.Parameter("value", new TypeReference("int"), false)),
                new TypeReference("int"), List.of()),
            null,
            new SymbolMetadata(modifiers, visibility, false, List.of(), Map.of()), List.of());
    }
}
