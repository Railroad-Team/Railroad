package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;

import static org.junit.jupiter.api.Assertions.*;

public class JavaProjectSemanticExtractorTest {

    @Test
    public void discoversOnlyNamedProjectTypesWithoutBuildingGraphFacts() {
        String source = """
            package demo;
            class Outer {
                interface Contract { class Nested {} }
                record Data(int value) {}
                class module {}
                enum Mode { ONE { class Hidden {} }; class Nested {} }
                @interface Marker { class Nested {} }
                Object field = new Object() { class Hidden {} };
                void run() { class Local {} }
                static { class Local {} }
            }
            """;
        Path path = Path.of("Outer.java");
        var snapshot = new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(path),
            DocumentVersion.initial(), "java", source, StandardCharsets.UTF_8);

        var discovered = new JavaProjectSemanticExtractor().discoverTypes(path, snapshot, () -> {
        });

        assertEquals("demo", discovered.packageName());
        assertEquals(Set.of("demo.Outer", "demo.Outer.Contract", "demo.Outer.Contract.Nested", "demo.Outer.Data",
            "demo.Outer.module",
            "demo.Outer.Mode", "demo.Outer.Mode.Nested", "demo.Outer.Marker", "demo.Outer.Marker.Nested"),
            discovered.declaredSymbols().stream().map(JavaProjectSemanticIndex.SymbolDescriptor::qualifiedName)
                .collect(Collectors.toSet()));
        assertNotNull(discovered.sourceHash());
        assertNull(discovered.semanticShard());
    }

    @Test
    public void extractsPackageImportsAndProjectSymbolsFromSource() {
        String source = """
            package demo.sample;
            import java.util.List;
            import static java.lang.Math.max;

            class Utility {
                static int VALUE;
                int count;

                Utility() {
                }

                int run(String text, int n) {
                    int local = max(n, 1);
                    return local;
                }

                class Inner {
                }

                enum Mode {
                    ACTIVE
                }
            }
            """;

        var extractor = new JavaProjectSemanticExtractor();
        JavaProjectSemanticIndex.SourceFileIndex fileIndex = extractor
            .extract(Path.of("src/main/java/demo/sample/Utility.java"), source);

        assertEquals("demo.sample", fileIndex.packageName());
        assertEquals(2, fileIndex.imports().size());
        assertImport(fileIndex.imports(), "java.util.List", false, false);
        assertImport(fileIndex.imports(), "java.lang.Math.max", true, false);

        JavaProjectSemanticIndex.SymbolDescriptor utility = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility");
        assertNotNull(utility);
        assertTrue(utility.isTopLevel());

        JavaProjectSemanticIndex.SymbolDescriptor value = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility#VALUE");
        assertNotNull(value);
        assertTrue(value.isStatic());
        assertEquals("demo.sample.Utility", value.ownerQualifiedName());

        JavaProjectSemanticIndex.SymbolDescriptor count = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility#count");
        assertNotNull(count);
        assertFalse(count.isStatic());

        JavaProjectSemanticIndex.SymbolDescriptor constructor = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility#Utility");
        assertNotNull(constructor);
        assertEquals("()", constructor.signature());

        JavaProjectSemanticIndex.SymbolDescriptor run = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility#run");
        assertNotNull(run);
        assertEquals("(String,int)", run.signature());

        JavaProjectSemanticIndex.SymbolDescriptor inner = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility.Inner");
        assertNotNull(inner);
        assertFalse(inner.isTopLevel());

        JavaProjectSemanticIndex.SymbolDescriptor enumConstant = findSymbol(fileIndex.declaredSymbols(),
            "demo.sample.Utility.Mode#ACTIVE");
        assertNotNull(enumConstant);
        assertTrue(enumConstant.isStatic());

        assertTrue(fileIndex.declaredSymbols().stream().noneMatch(symbol -> "local".equals(symbol.simpleName())));
    }

    @Test
    public void preservesVarargsInCallableSignatures() {
        JavaProjectSemanticIndex.SourceFileIndex fileIndex = new JavaProjectSemanticExtractor().extract(
            Path.of("Box.java"), """
                class Box<T> {
                    Box(T value, Box<T>... children) {}
                    void add(String name, int... values) {}
                }
                """);

        JavaProjectSemanticIndex.SymbolDescriptor constructor = fileIndex.declaredSymbols().stream()
            .filter(symbol -> symbol.kind() == JavaSymbolKind.CONSTRUCTOR)
            .findFirst()
            .orElseThrow();
        JavaProjectSemanticIndex.SymbolDescriptor method = fileIndex.declaredSymbols().stream()
            .filter(symbol -> symbol.kind() == JavaSymbolKind.METHOD)
            .findFirst()
            .orElseThrow();

        assertEquals("(T,Box<T>[])", constructor.signature());
        assertEquals("(String,int[])", method.signature());
    }

    private static void assertImport(
        List<JavaProjectSemanticIndex.ImportDescriptor> imports,
        String qualifiedName,
        boolean isStatic,
        boolean isWildcard
    ) {
        assertTrue(imports.stream().anyMatch(importDescriptor -> qualifiedName.equals(importDescriptor.qualifiedName())
            && isStatic == importDescriptor.isStatic()
            && isWildcard == importDescriptor.isWildcard()));
    }

    private static JavaProjectSemanticIndex.SymbolDescriptor findSymbol(
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols,
        String qualifiedName
    ) {
        return symbols.stream()
            .filter(symbol -> qualifiedName.equals(symbol.qualifiedName()))
            .findFirst()
            .orElse(null);
    }
}
