package dev.railroadide.railroad.ide.sst.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaProjectSemanticExtractorTest {

    @Test
    void extractsPackageImportsAndProjectSymbolsFromSource() {
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
                }
                """;

        JavaProjectSemanticExtractor extractor = new JavaProjectSemanticExtractor();
        JavaProjectSemanticIndex.SourceFileIndex fileIndex = extractor.extract(Path.of("src/main/java/demo/sample/Utility.java"), source);

        assertEquals("demo.sample", fileIndex.packageName());
        assertEquals(2, fileIndex.imports().size());
        assertImport(fileIndex.imports(), "java.util.List", false, false);
        assertImport(fileIndex.imports(), "java.lang.Math.max", true, false);

        JavaProjectSemanticIndex.SymbolDescriptor utility = findSymbol(fileIndex.declaredSymbols(), "demo.sample.Utility");
        assertNotNull(utility);
        assertTrue(utility.isTopLevel());

        JavaProjectSemanticIndex.SymbolDescriptor value = findSymbol(fileIndex.declaredSymbols(), "demo.sample.Utility#VALUE");
        assertNotNull(value);
        assertTrue(value.isStatic());
        assertEquals("demo.sample.Utility", value.ownerQualifiedName());

        JavaProjectSemanticIndex.SymbolDescriptor count = findSymbol(fileIndex.declaredSymbols(), "demo.sample.Utility#count");
        assertNotNull(count);
        assertFalse(count.isStatic());

        JavaProjectSemanticIndex.SymbolDescriptor constructor = findSymbol(fileIndex.declaredSymbols(), "demo.sample.Utility#Utility");
        assertNotNull(constructor);
        assertEquals("()", constructor.signature());

        JavaProjectSemanticIndex.SymbolDescriptor run = findSymbol(fileIndex.declaredSymbols(), "demo.sample.Utility#run");
        assertNotNull(run);
        assertEquals("(String,int)", run.signature());

        JavaProjectSemanticIndex.SymbolDescriptor inner = findSymbol(fileIndex.declaredSymbols(), "demo.sample.Utility.Inner");
        assertNotNull(inner);
        assertFalse(inner.isTopLevel());

        assertTrue(fileIndex.declaredSymbols().stream().noneMatch(symbol -> "local".equals(symbol.simpleName())));
    }

    private static void assertImport(
            List<JavaProjectSemanticIndex.ImportDescriptor> imports,
            String qualifiedName,
            boolean isStatic,
            boolean isWildcard
    ) {
        assertTrue(imports.stream().anyMatch(importDescriptor ->
                qualifiedName.equals(importDescriptor.qualifiedName())
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
