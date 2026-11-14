package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaSourcePrecedenceIndex;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.graph.api.EdgeKindId;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

public class JavaBinarySymbolGraphTest {
    @TempDir
    public Path directory;

    @Test
    public void compiledDeclarationsAgreeOnConstructorParametersArrayReturnsAndEnumModifiers() throws Exception {
        String source = """
            package sample;
            class Outer {
                class Inner { Inner(@Deprecated int count) {} }
                class Generic { <T> Generic(@Deprecated T value) {} }
                static class Nested { Nested(Outer owner, int count) {} }
            }
            class Arrays {
                int trailing()[] { return null; }
                int[] mixed()[][] { return null; }
                int[] leading() { return null; }
            }
            enum Mode { A(1); Mode(int count) {} }
            """;
        Path file = directory.resolve("Fixture.java");
        Files.writeString(file, source);
        var sourceShard = new JavaProjectSemanticExtractor().extract(file, source).semanticShard();
        for (boolean parameterNames : List.of(false, true)) {
            var arguments = new ArrayList<>(List.of("-d", directory.toString(), file.toString()));
            if (parameterNames) {
                arguments.addFirst("-parameters");
            }
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                arguments.toArray(String[]::new)));
            for (String name : List.of("Outer$Inner", "Outer$Generic", "Outer$Nested", "Arrays", "Mode")) {
                Path binary = directory.resolve("sample/" + name + ".class");
                var stub = ClassStubParser.parse(new ClassReader(Files.readAllBytes(binary)));
                if (List.of("Outer$Inner", "Outer$Generic").contains(name)) {
                    assertTrue(stub.constructors().getFirst().parameters().getFirst().annotations().stream()
                        .anyMatch(annotation -> annotation.name().equals("java.lang.Deprecated")));
                }
                var shard = JavaBinarySymbolExtractor.extract(stub, DocumentUri.fromPath(binary),
                    Map.of(), () -> {
                    });
                var owner = JavaSymbolIds.type("sample." + name);
                for (var declaration : sourceShard.declarations()) {
                    if (!owner.equals(declaration.ownerId())
                        || !List.of("java:method", "java:constructor").contains(declaration.kind().id()))
                        continue;
                    var compiled = shard.declarations().stream().filter(symbol -> symbol.id().equals(declaration.id()))
                        .findFirst().orElseThrow(() -> new AssertionError(declaration.id()));
                    assertEquals(declaration.signature().parameters().size(), compiled.signature().parameters().size());
                    assertEquals(declaration.metadata().visibility(), compiled.metadata().visibility());
                    if (declaration.signature().returnType() != null) {
                        assertEquals(compiled.signature().returnType().arrayDimensions(),
                            declaration.signature().returnType().arrayDimensions());
                        assertEquals(compiled.declaredType().arrayDimensions(),
                            declaration.declaredType().arrayDimensions());
                    }
                    for (int i = 0; i < compiled.signature().parameters().size(); i++) {
                        var parameterId = JavaSymbolIds.parameter(compiled.id(), i);
                        var parameter = shard.declarations().stream().filter(symbol -> symbol.id().equals(parameterId))
                            .findFirst().orElseThrow();
                        var sourceParameter = sourceShard.declarations().stream()
                            .filter(symbol -> symbol.id().equals(parameterId))
                            .findFirst().orElseThrow();
                        if (parameterNames) {
                            assertEquals(sourceParameter.simpleName(), parameter.simpleName());
                        }
                    }
                }
                if (name.equals("Mode")) {
                    assertTrue(sourceShard.declarations().stream().filter(symbol -> symbol.id().equals(owner))
                        .findFirst().orElseThrow().metadata().modifiers().contains("final"));
                }
            }
        }
    }

    @Test
    public void archivesRetainDistinctJarAndJmodEntryAddresses() throws Exception {
        Path jar = archive("library.jar", "", false);
        Path jmod = archive("library.jmod", "classes/", true);
        for (Path archive : List.of(jar, jmod)) {
            var index = JavaLibrarySymbolIndex.build(List.of(archive));
            assertEquals(2, index.semanticShards().size());
            assertEquals(2,
                new HashSet<>(index.semanticShards().stream().map(SemanticIndexShard::documentId).toList()).size());
            for (String type : List.of("sample.Box", "sample.Other")) {
                var location = index.binaryLocations().get(type);
                assertNotNull(location);
                String expected = "!/" + (archive.equals(jmod) ? "classes/" : "") + type.replace('.', '/') + ".class";
                assertTrue(location.toString().endsWith(expected), location.toString());
                var symbol = index.semanticShards().stream().flatMap(shard -> shard.declarations().stream())
                    .filter(declaration -> declaration.id().equals(JavaSymbolIds.type(type))).findFirst().orElseThrow();
                assertEquals(location, symbol.locations().getFirst().uri());
                assertEquals(BackingKind.ARCHIVE_ENTRY, symbol.locations().getFirst().origin().backing());
            }
        }
    }

    @Test
    public void genericArrayAndVariadicMethodIdsMatchSourceAndKeepStructuredDetail() {
        var stub = ClassStubParser.parse(new ClassReader(classBytes("sample/Box", true)));
        var uri = DocumentUri.virtual("binary", "Box.class");
        var shard = JavaBinarySymbolExtractor.extract(stub, uri, Map.of(), () -> {
        });
        SymbolId expected = JavaSymbolIds.method(JavaSymbolIds.type("sample.Box"), "combine",
            List.of("java.lang.Number", "java.lang.CharSequence", "int[][]", "java.lang.String[]"));
        SymbolDescriptor method = shard.declarations().stream().filter(symbol -> symbol.id().equals(expected))
            .findFirst().orElseThrow();
        assertEquals("T", method.signature().returnType().name());
        assertEquals("java.lang.CharSequence",
            method.signature().typeParameters().getFirst().bounds().getFirst().name());
        assertEquals(2, method.signature().parameters().get(2).type().arrayDimensions());
        assertTrue(method.signature().parameters().getLast().varargs());
        assertEquals(0, method.signature().parameters().getLast().type().arrayDimensions());
        assertFalse(shard.declarations().getFirst().metadata().modifiers().contains("synchronized"));
        assertFalse(method.metadata().modifiers().contains("transient"));

        var model = JavaSemanticAnalyzer.analyzeDeclarationsFacts("""
            package sample;
            abstract class Box<T extends java.lang.Number> {
                abstract <U extends java.lang.CharSequence> T combine(T first, U second, int[][] grid, String... rest);
            }
            """);
        List<Symbol> declared = new ArrayList<>();
        collect(model, model.syntaxTree().root(), declared);
        Symbol source = declared.stream().filter(symbol -> symbol.simpleName().equals("combine")).findFirst()
            .orElseThrow();
        assertEquals(expected, source.id().orElseThrow());
    }

    @Test
    public void graphEnrichmentReusesLookupDeclarationsAndPublishesOnlyCompleteResults() {
        var stub = ClassStubParser.parse(new ClassReader(classBytes("sample/Box", true)));
        var uri = DocumentUri.virtual("binary", "Box.class");
        var minimal = JavaBinarySymbolExtractor.extractDeclarations(stub, uri, Map.of(), () -> {
        });
        assertTrue(minimal.declarations().stream().noneMatch(symbol -> symbol.kind().id().equals("java:parameter")));
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class,
            () -> JavaBinarySymbolExtractor.withParameters(minimal, Map.of(), () -> {
                if (checks.incrementAndGet() == 4)
                    throw new CancellationException();
            }));
        var complete = JavaBinarySymbolExtractor.withParameters(minimal, Map.of(), () -> {
        });
        assertTrue(complete.declarations().size() > minimal.declarations().size());
        for (var declaration : minimal.declarations()) {
            assertSame(declaration,
                complete.declarations().stream().filter(symbol -> symbol.id().equals(declaration.id()))
                    .findFirst().orElseThrow());
        }
        var index = new JavaStubSymbolIndex(Map.of("sample.Box", stub),
            Map.of("sample.Box", directory.resolve("Box.class")));
        var published = index.lookupQualifiedName("sample.Box").getFirst().descriptor();
        var shards = index.semanticShards();
        assertSame(shards, index.semanticShards());
        assertSame(published, shards.getFirst().declarations().getFirst());
    }

    @Test
    public void concurrentBinaryLookupsReuseDeclarationsAndInterruptedReadsCanRetry() throws Exception {
        var stub = ClassStubParser.parse(new ClassReader(classBytes("sample/Box", true)));
        var index = new JavaStubSymbolIndex(Map.of("sample.Box", stub),
            Map.of("sample.Box", directory.resolve("Box.class")));
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> index.lookupMembers("sample.Box"));
        } finally {
            Thread.interrupted();
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<List<JavaProjectSemanticIndex.SymbolDescriptor>> lookup = () -> index.lookupMembers("sample.Box");
            var results = executor.invokeAll(Collections.nCopies(8, lookup));
            List<JavaProjectSemanticIndex.SymbolDescriptor> members = results.getFirst().get();
            assertFalse(members.isEmpty());
            for (var result : results) {
                assertSame(members, result.get());
            }
            var method = index.lookupMember("sample.Box", "combine").getFirst();
            assertSame(method, index.lookupQualifiedName("sample.Box#combine").getFirst());
            assertSame(method, index.lookupSimpleName("combine").getFirst());
            assertSame(method.descriptor(), index.semanticShards().getFirst().declarations().stream()
                .filter(declaration -> declaration.id().equals(method.descriptor().id())).findFirst().orElseThrow());
            assertThrows(UnsupportedOperationException.class, members::clear);
        }
    }

    @Test
    public void capturedGraphPassesCallbackCancellationThroughLazyBinaryMaterialization() {
        var stub = ClassStubParser.parse(new ClassReader(classBytes("sample/Box", true)));
        var binaryStarted = new AtomicBoolean();
        var binary = new JavaStubSymbolIndex(Map.of("sample.Box", stub),
            Map.of("sample.Box", directory.resolve("Box.class"))) {
            @Override
            public List<SemanticIndexShard> semanticShards(Runnable activityCheck) {
                binaryStarted.set(true);
                return super.semanticShards(activityCheck);
            }
        };
        var sources = JavaProjectSemanticIndex.empty();
        var combined = new JavaSourcePrecedenceIndex(sources, new CompositeJavaSymbolIndex(List.of(binary)));
        var analysis = new JavaWorkspaceAnalysis(sources, combined, Map.of(), List.of(), directory);
        var binaryChecks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> analysis.resolvedGraph(() -> {
            if (binaryStarted.get() && binaryChecks.incrementAndGet() == 5)
                throw new CancellationException("request cancelled without interrupting its worker");
        }));
        assertEquals(5, binaryChecks.get());
        assertFalse(Thread.currentThread().isInterrupted());
        var completed = analysis.resolvedGraph(() -> {
        });
        assertSame(completed, analysis.resolvedGraph(() -> {
        }));
        assertTrue(binary.semanticShards().getFirst().declarations().stream()
            .anyMatch(declaration -> declaration.kind().id().equals("java:parameter")));
    }

    @Test
    public void attachedSourceAndDecompilerLocationsShareOneBinaryIdentity() {
        var stub = ClassStubParser.parse(new ClassReader(classBytes("sample/Other", false)));
        SymbolId id = JavaSymbolIds.type("sample.Other");
        var attached = new SymbolLocation(DocumentUri.virtual("source", "Other.java"),
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.ATTACHED_SOURCE), null, null, true);
        var decompiled = new SymbolLocation(DocumentUri.virtual("decompiled", "Other.java"),
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.DECOMPILED), null, null, true);
        var alternatives = new ArrayList<>(List.of(attached, decompiled));
        var shard = JavaBinarySymbolExtractor.extract(stub, DocumentUri.virtual("binary", "Other.class"),
            Map.of(id, alternatives), () -> {
            });
        alternatives.clear();
        var symbol = shard.declarations().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow();
        assertEquals(3, symbol.locations().size());
        assertEquals(attached, symbol.locations().get(1));
        assertEquals(decompiled, symbol.locations().get(2));
        assertEquals(1, shard.declarations().stream().filter(candidate -> candidate.id().equals(id)).count());
    }

    @Test
    public void cancellationDoesNotPublishPartialBinaryResults() throws Exception {
        Path jar = archive("cancel.jar", "", false);
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> JavaLibrarySymbolIndex.build(List.of(jar), () -> {
            if (checks.incrementAndGet() == 4)
                throw new CancellationException("cancel scan");
        }));
        var stub = ClassStubParser.parse(new ClassReader(classBytes("sample/Box", true)));
        checks.set(0);
        assertThrows(CancellationException.class, () -> JavaBinarySymbolExtractor.extract(stub,
            DocumentUri.virtual("binary", "Box.class"), Map.of(), () -> {
                if (checks.incrementAndGet() == 2)
                    throw new CancellationException("cancel extraction");
            }));
    }

    @Test
    public void unnamedPackageBinaryUsesSameIdentityAsSource() {
        var stub = ClassStubParser.parse(new ClassReader(classBytes("Example", false)));
        assertEquals("Example", stub.getFullName());
        var shard = JavaBinarySymbolExtractor.extract(stub, DocumentUri.virtual("binary", "Example.class"),
            Map.of(), () -> {
            });
        assertEquals(JavaSymbolIds.type("Example"), shard.declarations().getFirst().id());
    }

    @Test
    public void jvmReturnOnlyOverloadsRemainDistinctWithoutChangingOrdinaryMethodIds() {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "sample/ReturnOverloads", null,
            "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "invoke", "(I)I", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "invoke", "(I)J", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "normal", "(I)I", null, null).visitEnd();
        writer.visitEnd();
        var stub = ClassStubParser.parse(new ClassReader(writer.toByteArray()));
        var shard = JavaBinarySymbolExtractor.extract(stub, DocumentUri.virtual("binary", "ReturnOverloads.class"),
            Map.of(), () -> {
            });
        SymbolId owner = JavaSymbolIds.type("sample.ReturnOverloads");
        List<SymbolId> ids = shard.declarations().stream().map(SymbolDescriptor::id).toList();
        assertTrue(ids.contains(JavaSymbolIds.binaryMethod(owner, "invoke", List.of("int"), "int")));
        assertTrue(ids.contains(JavaSymbolIds.binaryMethod(owner, "invoke", List.of("int"), "long")));
        assertTrue(ids.contains(JavaSymbolIds.method(owner, "normal", List.of("int"))));
        assertFalse(ids.contains(JavaSymbolIds.method(owner, "invoke", List.of("int"))));
        assertEquals(ids.size(), new HashSet<>(ids).size());
    }

    @Test
    public void indexesCurrentRuntimeWithoutCollidingJvmDeclarations() {
        var index = JavaJdkSymbolIndex.fromCurrentRuntime();
        assertFalse(index.lookupQualifiedName("java.lang.String").isEmpty());
        assertTrue(index.semanticShards().stream().flatMap(shard -> shard.declarations().stream())
            .anyMatch(symbol -> symbol.id().equals(JavaSymbolIds.type("java.lang.String"))));
        for (var shard : index.semanticShards()) {
            assertEquals(shard.declarations().size(),
                new HashSet<>(shard.declarations().stream().map(SymbolDescriptor::id).toList()).size());
        }
    }

    @Test
    public void resolvedGenericBinaryCallUsesDeclarationErasureIdentity() {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "sample/Generic", null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "identity",
            "(Ljava/lang/Number;)Ljava/lang/Number;", "<T:Ljava/lang/Number;>(TT;)TT;", null).visitEnd();
        writer.visitEnd();
        var stub = ClassStubParser.parse(new ClassReader(writer.toByteArray()));
        var index = new JavaStubSymbolIndex(Map.of("sample.Generic", stub),
            Map.of("sample.Generic", directory.resolve("Generic.class")));
        var model = JavaSemanticAnalyzer.analyzeFacts("""
            import sample.Generic;
            class Use { Number result() { return Generic.identity(1); } }
            """, index);
        SymbolId expected = JavaSymbolIds.method(JavaSymbolIds.type("sample.Generic"), "identity",
            List.of("java.lang.Number"));
        List<Symbol> resolved = new ArrayList<>();
        collectResolved(model, model.syntaxTree().root(), resolved);
        Symbol call = resolved.stream().filter(symbol -> symbol.simpleName().equals("identity")).findFirst()
            .orElseThrow();
        assertEquals(expected, call.id().orElseThrow());
        assertTrue(index.semanticShards().stream().flatMap(shard -> shard.declarations().stream())
            .anyMatch(symbol -> symbol.id().equals(expected)));
    }

    @Test
    public void binaryGenericSuperclassArgumentsLinkSpecializedOverrides() {
        var baseWriter = new ClassWriter(0);
        baseWriter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "sample/Base",
            "<T:Ljava/lang/Object;>Ljava/lang/Object;", "java/lang/Object", null);
        baseWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "accept", "(Ljava/lang/Object;)V",
            "(TT;)V", null).visitEnd();
        baseWriter.visitEnd();
        var childWriter = new ClassWriter(0);
        childWriter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "sample/Child",
            "Lsample/Base<Ljava/lang/String;>;", "sample/Base", null);
        childWriter.visitMethod(Opcodes.ACC_PUBLIC, "accept", "(Ljava/lang/String;)V", null, null).visitEnd();
        childWriter.visitEnd();
        var base = JavaBinarySymbolExtractor.extract(ClassStubParser.parse(new ClassReader(baseWriter.toByteArray())),
            DocumentUri.virtual("binary", "Base.class"), Map.of(), () -> {
            });
        var child = JavaBinarySymbolExtractor.extract(ClassStubParser.parse(new ClassReader(childWriter.toByteArray())),
            DocumentUri.virtual("binary", "Child.class"), Map.of(), () -> {
            });
        var inheritance = child.edges().stream().filter(edge -> edge.kind().equals(EdgeKindId.SUBTYPE)).findFirst()
            .orElseThrow();
        assertEquals("java.lang.String", inheritance.typeArguments().getFirst().name());
        var enriched = JavaOverrideGraphExtractor.extract(List.of(base, child), () -> {
        });
        SymbolId implementation = JavaSymbolIds.method(JavaSymbolIds.type("sample.Child"), "accept",
            List.of("java.lang.String"));
        SymbolId declaration = JavaSymbolIds.method(JavaSymbolIds.type("sample.Base"), "accept",
            List.of("java.lang.Object"));
        assertTrue(enriched.stream().flatMap(shard -> shard.edges().stream())
            .anyMatch(edge -> edge.kind().equals(EdgeKindId.OVERRIDE) && edge.source().equals(implementation)
                && edge.target().equals(declaration)));
    }

    private Path archive(String filename, String prefix, boolean jmod) throws Exception {
        var bytes = new ByteArrayOutputStream();
        if (jmod) {
            bytes.write(new byte[]{'J', 'M', 1, 0});
        }
        try (var jar = new JarOutputStream(bytes)) {
            for (String name : List.of("sample/Box", "sample/Other")) {
                jar.putNextEntry(new JarEntry(prefix + name + ".class"));
                jar.write(classBytes(name, false));
                jar.closeEntry();
            }
        }
        Path path = directory.resolve(filename);
        Files.write(path, bytes.toByteArray());
        return path;
    }

    private static byte[] classBytes(String name, boolean generic) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER, name,
            generic ? "<T:Ljava/lang/Number;>Ljava/lang/Object;" : null, "java/lang/Object", null);
        if (generic) {
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_VARARGS, "combine",
                "(Ljava/lang/Number;Ljava/lang/CharSequence;[[I[Ljava/lang/String;)Ljava/lang/Number;",
                "<U:Ljava/lang/CharSequence;>(TT;TU;[[I[Ljava/lang/String;)TT;", null).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void collect(SemanticModel model, SyntaxNode node, List<Symbol> symbols) {
        model.declaredSymbol(node).ifPresent(symbols::add);
        for (SyntaxNode child : node.children()) {
            collect(model, child, symbols);
        }
    }

    private static void collectResolved(SemanticModel model, SyntaxNode node, List<Symbol> symbols) {
        model.resolvedSymbol(node).ifPresent(symbols::add);
        for (SyntaxNode child : node.children()) {
            collectResolved(model, child, symbols);
        }
    }
}
