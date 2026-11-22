package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

public class JavaSemanticGraphExtractorTest {
    @Test
    public void callsSelectMethodNamesEvenWhenReceiversAndArgumentsHaveTheSameName() {
        String source = "class Calls { void run(Calls value) {} void test(Calls run) { run.run(run); this.run(run); } }";
        var shard = JavaSemanticGraphExtractor.extract(
            JavaSemanticAnalyzer.analyzeFacts(source, JavaProjectSemanticIndex.empty()), () -> {
            });
        var calls = shard.references().stream().filter(reference -> reference.kind().equals(ReferenceKindId.CALL))
            .toList();
        assertEquals(List.of(source.indexOf("run.run") + 4, source.indexOf("this.run") + 5),
            calls.stream().map(reference -> reference.location().range().start).toList());
        calls.forEach(reference -> assertEquals("run",
            source.substring(reference.location().range().start, reference.location().range().end)));
        assertTrue(shard.references().stream().anyMatch(reference -> reference.kind().equals(ReferenceKindId.READ)
            && reference.location().range().start == source.indexOf("run.run")));
    }

    @Test
    public void enumConstantBodiesPreventImplicitFinality() {
        var shard = new JavaProjectSemanticExtractor().extract(Path.of("Mode.java"),
            "enum Mode { A { void work() {} }; Mode() {} }").semanticShard();
        assertFalse(named(shard, "Mode").metadata().modifiers().contains("final"));
        assertTrue(named(shard, "Mode").metadata().modifiers().contains("sealed"));
    }

    @Test
    public void constantSpecificMethodsBelongToDistinctSubtypesOfTheEnum() {
        String source = """
            enum Mode {
                A { int value() { return 1; } },
                B { int value() { return 2; } };
                abstract int value();
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source, JavaProjectSemanticIndex.empty());
        var shard = JavaSemanticGraphExtractor.extract(model, () -> {
        });
        var enumId = named(shard, "Mode").id();
        var subtypeIds = shard.edges().stream()
            .filter(edge -> edge.kind().equals(EdgeKindId.SUBTYPE) && edge.target().equals(enumId))
            .map(SymbolEdge::source).collect(Collectors.toSet());
        assertEquals(2, subtypeIds.size());
        var owners = shard.declarations().stream().filter(symbol -> symbol.simpleName().equals("value"))
            .map(SymbolDescriptor::ownerId).collect(Collectors.toSet());
        assertTrue(owners.remove(enumId));
        assertEquals(subtypeIds, owners);
    }

    @Test
    public void anonymousTypesRetainResolvedGenericSubtypeEdges() {
        String source = """
            class Parent<T> {}
            class Example {
                Object value = new Parent<String>() {};
                Object missing = new Missing() {};
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source, JavaProjectSemanticIndex.empty());
        var shard = JavaSemanticGraphExtractor.extract(model, () -> {
        });
        var anonymous = shard.declarations().stream()
            .filter(symbol -> symbol.simpleName().equals("<anonymous>")).toList();
        assertEquals(2, anonymous.size());
        var edges = shard.edges().stream().filter(edge -> edge.kind().equals(EdgeKindId.SUBTYPE)).toList();
        assertEquals(1, edges.size());
        assertEquals(anonymous.getFirst().id(), edges.getFirst().source());
        assertEquals(named(shard, "Parent").id(), edges.getFirst().target());
        assertEquals("java.lang.String", edges.getFirst().typeArguments().getFirst().name());
        var inheritance = shard.references().stream()
            .filter(reference -> reference.kind().equals(ReferenceKindId.INHERITANCE)).toList();
        assertEquals(1, inheritance.size());
        assertEquals(anonymous.getFirst().id(), inheritance.getFirst().enclosingSymbol());
        assertEquals(named(shard, "Parent").id(), inheritance.getFirst().target());
        assertEquals("Parent", source.substring(inheritance.getFirst().location().range().start,
            inheritance.getFirst().location().range().end));
    }

    @Test
    public void retainsStructuredSignaturesModifiersAndExactNameRanges() {
        String source = """
            package sample;
            import java.util.List;
            class Example {
                @Deprecated public static <T extends Number> List<T[]> transform(final T value, String... names) throws Exception { return null; }
                void ordinary() { class Nested { static int unrelated; } }
            }
            """;
        var indexed = new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), source);
        var method = named(indexed.semanticShard(), "transform");
        assertEquals("List", method.signature().returnType().name());
        assertEquals(1, method.signature().returnType().arguments().getFirst().arrayDimensions());
        assertEquals("Number", method.signature().typeParameters().getFirst().bounds().getFirst().name());
        assertTrue(method.signature().parameters().get(1).varargs());
        assertEquals("Exception", method.signature().thrownTypes().getFirst().name());
        assertEquals(Set.of("public", "static"), method.metadata().modifiers());
        assertTrue(method.metadata().deprecated());
        assertFalse(named(indexed.semanticShard(), "ordinary").metadata().modifiers().contains("static"));
        var location = (Location.TextLocation) method.locations().getFirst().selection();
        assertEquals("transform", source.substring(location.range().start, location.range().end));
        assertEquals(JavaSymbolIds.method(JavaSymbolIds.type("sample.Example"), "transform",
            List.of("java.lang.Number", "java.lang.String[]")), method.id());
    }

    @Test
    public void extractsResolvedReadsWritesCallsConstructorsAndDocumentation() {
        String source = """
            class Example {
                int value;
                Example() {}
                /** {@link #value} {@link #read()} */
                int read() { return value; }
                void change() { value = 1; value += 2; value++; read(); new Example(); missing(); }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        var shard = new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), model).semanticShard();
        var field = named(shard, "value").id();
        assertTrue(shard.references().stream().anyMatch(reference -> reference.target().equals(field)
            && reference.kind().equals(ReferenceKindId.WRITE)));
        assertTrue(shard.references().stream().anyMatch(reference -> reference.target().equals(field)
            && reference.kind().equals(ReferenceKindId.READ)));
        assertTrue(shard.references().stream().anyMatch(reference -> reference.target().equals(field)
            && reference.kind().equals(ReferenceKindId.DOCUMENTATION)));
        assertTrue(shard.edges().stream().anyMatch(edge -> edge.kind().equals(EdgeKindId.CALL)
            && edge.target().equals(named(shard, "read").id())));
        assertTrue(shard.references().stream().anyMatch(reference -> reference.kind().equals(ReferenceKindId.CALL)
            && reference.target().value().contains("/constructor/")));
        assertEquals(2,
            shard.references().stream().filter(reference -> reference.kind().equals(ReferenceKindId.CALL)).count());
    }

    @Test
    public void identitiesSurviveTriviaAndSeparateLocalScopesAndOverloads() {
        String source = """
            package p;
            import java.util.*;
            class Example {
                void run(java.util.List<String> items) { { int same = 1; } { int same = 2; } }
                void run(int value) {}
                class Nested {}
            }
            """;
        var extractor = new JavaProjectSemanticExtractor();
        var first = extractor.extract(Path.of("Example.java"), source).semanticShard();
        var second = extractor.extract(Path.of("Example.java"), source.replace("{", "{ /* comment */\n "))
            .semanticShard();
        assertEquals(first.declarations().stream().map(SymbolDescriptor::id).collect(Collectors.toSet()),
            second.declarations().stream().map(SymbolDescriptor::id).collect(Collectors.toSet()));
        assertEquals(2, first.declarations().stream().filter(symbol -> symbol.simpleName().equals("same")).count());
        assertEquals(JavaSymbolIds.type("p.Example$Nested"), named(first, "Nested").id());
        var alias = extractor.extract(Path.of("Example.java"),
            source.replace("java.util.List<String>", "List<String>"));
        assertEquals(
            first.declarations().stream().filter(symbol -> symbol.simpleName().equals("run")).map(SymbolDescriptor::id)
                .toList(),
            alias.semanticShard().declarations().stream().filter(symbol -> symbol.simpleName().equals("run"))
                .map(SymbolDescriptor::id).toList());
    }

    @Test
    public void retainsCapturedVersionAndCancelsWithoutReturningPartialFacts() {
        var id = DocumentId.create();
        var uri = DocumentUri.fromPath(Path.of("Example.java"));
        var snapshot = new TextDocumentSnapshot(id, uri, DocumentVersion.initial().next(), "java", "class Example {}",
            StandardCharsets.UTF_8);
        var extractor = new JavaProjectSemanticExtractor();
        var shard = extractor.extract(Path.of("Example.java"), snapshot, () -> {
        }).semanticShard();
        assertEquals(id, shard.documentId());
        assertEquals(snapshot.version(), shard.documentVersion());
        shard.declarations().forEach(
            symbol -> symbol.locations().forEach(location -> location.declaration().validateAgainst(snapshot)));
        assertThrows(CancellationException.class, () -> JavaSemanticGraphExtractor.extract(
            JavaSemanticAnalyzer.analyzeFacts(JavaSyntaxParser.parse(snapshot)), () -> {
                throw new CancellationException();
            }));
    }

    @Test
    public void resolvesProjectWildcardSignaturesAndExternalDocumentation() {
        var extractor = new JavaProjectSemanticExtractor();
        var dependency = extractor.extract(Path.of("dependency/Item.java"),
            "package dependency; public class Item { public void accept(String value) {} }");
        var index = JavaProjectSemanticIndex.builder().putFile(dependency).build();
        String source = """
            package sample;
            import dependency.*;
            /** {@link Item} {@link Item#accept(String)} */
            class Example { void use(Item item) {} }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source, index);
        var shard = extractor.extract(Path.of("Example.java"), model, index, () -> {
        }).semanticShard();
        assertEquals(JavaSymbolIds.method(JavaSymbolIds.type("sample.Example"), "use", List.of("dependency.Item")),
            named(shard, "use").id());
        assertEquals(2, shard.references().stream()
            .filter(reference -> reference.kind().equals(ReferenceKindId.DOCUMENTATION)).count());
    }

    @Test
    public void erasesRecursiveGenericBoundsAndNestedTypeParameters() {
        var shard = new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), """
            package p;
            class Example {
                class Nested {}
                <T extends U, U extends Number> void run(T value, Nested nested) {}
            }
            """).semanticShard();
        assertEquals(
            JavaSymbolIds.method(JavaSymbolIds.type("p.Example"), "run",
                List.of("java.lang.Number", "p.Example$Nested")),
            named(shard, "run").id());
    }

    @Test
    public void partialProgramsRetainAvailableFactsWithoutInventingTargets() {
        for (String source : List.of("class Example { void run(java.util.List< value",
            "class Example<T extends> { void run( ) { missing(); } }",
            "class Example { void run(int... values, int other) {} }",
            "class Example { /** {@link Missing#unknown()} */ void run() { unknown(); } }")) {
            var model = JavaSemanticAnalyzer.analyzeFacts(source, JavaProjectSemanticIndex.empty());
            SemanticIndexShard shard = assertDoesNotThrow(() -> JavaSemanticGraphExtractor.extract(model,
                JavaProjectSemanticIndex.empty(), () -> {
                }), source);
            assertTrue(shard.references().stream().noneMatch(reference -> reference.kind().equals(ReferenceKindId.CALL)
                || reference.kind().equals(ReferenceKindId.DOCUMENTATION)), source);
        }
    }

    @Test
    public void partialDeclarationIndexOmitsUnresolvedWildcardCallableIdentity() {
        String source = "package sample; import dependency.*; class Example { void use(Item item) {} }";
        var extractor = new JavaProjectSemanticExtractor();
        var partial = extractor.extract(Path.of("Example.java"), source);
        assertTrue(partial.declaredSymbols().stream().anyMatch(symbol -> symbol.simpleName().equals("use")));
        assertTrue(
            partial.semanticShard().declarations().stream().noneMatch(symbol -> symbol.simpleName().equals("use")));
        var dependency = extractor.extract(Path.of("Item.java"), "package dependency; public class Item {}");
        var index = JavaProjectSemanticIndex.builder().putFile(dependency).putFile(partial).build();
        var complete = extractor
            .extract(Path.of("Example.java"), JavaSemanticAnalyzer.analyzeFacts(source, index), index, () -> {
            }).semanticShard();
        assertEquals(JavaSymbolIds.method(JavaSymbolIds.type("sample.Example"), "use", List.of("dependency.Item")),
            named(complete, "use").id());
    }

    @Test
    public void indexesAnnotatedVarargsRecordAccessorAndCanonicalConstructor() {
        String source = """
            import java.lang.annotation.*;
            @Target(ElementType.TYPE_USE) @interface Mark {}
            record Batch<T>(T value, @Mark String[] @Mark ... conditions) { Batch {} }
            """;
        var shard = new JavaProjectSemanticExtractor().extract(Path.of("Batch.java"), source).semanticShard();
        var record = JavaSymbolIds.type("Batch");
        var accessor = shard.declarations().stream()
            .filter(symbol -> symbol.id().equals(JavaSymbolIds.method(record, "conditions", List.of())))
            .findFirst().orElseThrow();
        assertEquals("String[][]", accessor.signature().returnType().displayName());
        var constructor = shard.declarations().stream()
            .filter(symbol -> symbol.id()
                .equals(JavaSymbolIds.constructor(record, List.of("java.lang.Object", "java.lang.String[][]"))))
            .findFirst().orElseThrow();
        assertTrue(constructor.signature().parameters().getLast().varargs());
        assertEquals("String[]", constructor.signature().parameters().getLast().type().displayName());
    }

    @Test
    public void incompleteRecordComponentDoesNotCreateAnAccessor() {
        var shard = new JavaProjectSemanticExtractor().extract(Path.of("Batch.java"),
            "record Batch(int valid, String...) {}").semanticShard();
        assertEquals(List.of("valid"), shard.declarations().stream()
            .filter(symbol -> symbol.kind().id().equals("java:method"))
            .map(SymbolDescriptor::simpleName).toList());
    }

    @Test
    public void compactRecordConstructorUsesCanonicalComponentSignature() {
        var shard = new JavaProjectSemanticExtractor().extract(Path.of("Box.java"), "record Box(int value) { Box {} }")
            .semanticShard();
        var constructor = shard.declarations().stream().filter(symbol -> symbol.kind().id().equals("java:constructor"))
            .findFirst().orElseThrow();
        assertEquals(JavaSymbolIds.constructor(JavaSymbolIds.type("Box"), List.of("int")), constructor.id());
        assertEquals("int", constructor.signature().parameters().getFirst().type().name());
    }

    @Test
    public void localAndAnonymousDeclarationsStayOutOfProjectMemberLookups() {
        var file = new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), """
            class Example {
                void run() { class Local { void local() {} } Object value = new Object() { void anonymous() {} }; }
            }
            """);
        assertTrue(
            file.semanticShard().declarations().stream().anyMatch(symbol -> symbol.simpleName().equals("local")));
        assertTrue(
            file.semanticShard().declarations().stream().anyMatch(symbol -> symbol.simpleName().equals("anonymous")));
        assertTrue(file.declaredSymbols().stream()
            .noneMatch(symbol -> Set.of("Local", "local", "anonymous").contains(symbol.simpleName())));
    }

    @Test
    public void documentationDoesNotResolveBareMethodsOrOutOfScopeLocalTypes() {
        String source = "class Example { void owner() { class Hidden {} } /** {@link Hidden} {@link owner} */ void run() {} }";
        var model = JavaSemanticAnalyzer.analyzeFacts(source, JavaProjectSemanticIndex.empty());
        var shard = JavaSemanticGraphExtractor.extract(model, () -> {
        });
        assertTrue(
            shard.references().stream().noneMatch(reference -> reference.kind().equals(ReferenceKindId.DOCUMENTATION)));
    }

    @Test
    public void unresolvedEnclosingSignaturesOmitAnonymousIdsWithoutAbortingExtraction() {
        String source = "import missing.*; class Example { void run(Widget value) { Object nested = new Object() {}; } int known; }";
        JavaProjectSemanticIndex.SourceFileIndex file = assertDoesNotThrow(
            () -> new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), source));
        assertTrue(
            file.semanticShard().declarations().stream().anyMatch(symbol -> symbol.simpleName().equals("known")));
        assertTrue(file.semanticShard().declarations().stream()
            .noneMatch(symbol -> symbol.simpleName().equals("<anonymous>")));

        String inherited = "import missing.*; class Base<T> {} class Example extends Base<Missing> {}";
        var model = JavaSemanticAnalyzer.analyzeFacts(inherited, JavaProjectSemanticIndex.empty());
        assertDoesNotThrow(() -> JavaSemanticGraphExtractor.extract(model, JavaProjectSemanticIndex.empty(), () -> {
        }));
    }

    private static SymbolDescriptor named(SemanticIndexShard shard, String name) {
        return shard.declarations().stream().filter(symbol -> symbol.simpleName().equals(name)).findFirst()
            .orElseThrow();
    }
}
