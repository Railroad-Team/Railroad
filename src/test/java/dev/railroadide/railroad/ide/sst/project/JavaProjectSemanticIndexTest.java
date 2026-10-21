package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class JavaProjectSemanticIndexTest {

    @Test
    public void publishedIndexCopiesInputsAndSupportsConcurrentReadsAfterBuilderReuse() throws Exception {
        Path path = Path.of("Example.java");
        var descriptor = new JavaProjectSemanticIndex.SymbolDescriptor(
            SymbolKind.CLASS, "Example", "demo.Example", null, null, path, false, true);
        var declarations = new ArrayList<>(List.of(descriptor));
        var imports = new ArrayList<>(
            List.of(new JavaProjectSemanticIndex.ImportDescriptor("java.util.List", false, false)));
        var file = new JavaProjectSemanticIndex.SourceFileIndex(path, "demo", imports, declarations);
        var builder = JavaProjectSemanticIndex.builder().putFile(file);
        var index = builder.build();
        declarations.clear();
        imports.clear();
        builder.removeFile(path);
        assertTrue(builder.build().files().isEmpty());
        assertEquals(1, file.imports().size());
        assertThrows(UnsupportedOperationException.class, () -> index.files().clear());
        assertThrows(UnsupportedOperationException.class, () -> file.declaredSymbols().clear());
        assertThrows(UnsupportedOperationException.class, () -> index.lookupSimpleName("Example").clear());
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int read = 0; read < 20; read++) {
                        assertSame(file, index.getFile(path).orElseThrow());
                        assertEquals(List.of(descriptor), index.lookupQualifiedName("demo.Example"));
                        assertEquals(Set.of("demo.Example"), index.declaredQualifiedNames());
                    }
                    return null;
                }));
            }
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            for (var task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void emptyIndexReturnsNoFilesOrSymbols() {
        JavaProjectSemanticIndex index = JavaProjectSemanticIndex.empty();

        assertTrue(index.files().isEmpty());
        assertFalse(index.containsFile(Path.of("src/main/java/demo/Utility.java")));
        assertTrue(index.getFile(Path.of("src/main/java/demo/Utility.java")).isEmpty());
        assertTrue(index.getFilesByPackage("demo").isEmpty());
        assertTrue(index.lookupSimpleName("Utility").isEmpty());
        assertTrue(index.lookupQualifiedName("demo.Utility").isEmpty());
        assertTrue(index.lookupMembers("demo.Utility").isEmpty());
        assertTrue(index.lookupMember("demo.Utility", "VALUE").isEmpty());
    }

    @Test
    public void indexesFilesPackagesSymbolsAndMembers() {
        Path utilityPath = Path.of("src/main/java/demo/Utility.java");
        Path usePath = Path.of("src/main/java/demo/Use.java");

        var utility = new JavaProjectSemanticIndex.SourceFileIndex(
            utilityPath,
            " demo ",
            List.of(),
            List.of(
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.CLASS,
                    "Utility",
                    "demo.Utility",
                    null,
                    null,
                    utilityPath,
                    false,
                    true),
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.FIELD,
                    "VALUE",
                    "demo.Utility#VALUE",
                    "demo.Utility",
                    null,
                    utilityPath,
                    true,
                    false),
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.METHOD,
                    "run",
                    "demo.Utility#run(String)",
                    "demo.Utility",
                    "(String)",
                    utilityPath,
                    false,
                    false)));

        var use = new JavaProjectSemanticIndex.SourceFileIndex(
            usePath,
            "demo",
            List.of(
                new JavaProjectSemanticIndex.ImportDescriptor("demo.Utility", false, false),
                new JavaProjectSemanticIndex.ImportDescriptor("demo.Utility.VALUE", true, false)),
            List.of(
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.CLASS,
                    "Use",
                    "demo.Use",
                    null,
                    null,
                    usePath,
                    false,
                    true)));

        JavaProjectSemanticIndex index = JavaProjectSemanticIndex.builder()
            .putFile(utility)
            .putFile(use)
            .build();

        assertEquals(2, index.files().size());
        assertTrue(index.containsFile(utility.path()));
        assertEquals(utility, index.getFile(utilityPath).orElseThrow());
        assertEquals(2, index.getFilesByPackage("demo").size());
        assertEquals(1, index.lookupSimpleName("Utility").size());
        assertEquals(1, index.lookupQualifiedName("demo.Utility").size());
        assertEquals(2, index.lookupMembers("demo.Utility").size());
        assertEquals(1, index.lookupMember("demo.Utility", "VALUE").size());
        assertEquals(1, index.lookupMember("demo.Utility", "run").size());
    }

    @Test
    public void builderRemoveFileRemovesIndexedDeclarations() {
        Path utilityPath = Path.of("src/main/java/demo/Utility.java");

        var utility = new JavaProjectSemanticIndex.SourceFileIndex(
            utilityPath,
            "demo",
            List.of(),
            List.of(
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.CLASS,
                    "Utility",
                    "demo.Utility",
                    null,
                    null,
                    utilityPath,
                    false,
                    true)));

        JavaProjectSemanticIndex index = JavaProjectSemanticIndex.builder()
            .putFile(utility)
            .removeFile(Path.of("src/main/java/demo/./Utility.java"))
            .build();

        assertFalse(index.containsFile(utility.path()));
        assertTrue(index.getFile(utilityPath).isEmpty());
        assertTrue(index.lookupSimpleName("Utility").isEmpty());
        assertTrue(index.lookupQualifiedName("demo.Utility").isEmpty());
    }

    @Test
    public void sourceFileIndexCollectsOnlyNonNullQualifiedNames() {
        Path path = Path.of("src/main/java/demo/Utility.java");

        var file = new JavaProjectSemanticIndex.SourceFileIndex(
            path,
            "demo",
            List.of(),
            List.of(
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.CLASS,
                    "Utility",
                    "demo.Utility",
                    null,
                    null,
                    path,
                    false,
                    true),
                new JavaProjectSemanticIndex.SymbolDescriptor(
                    SymbolKind.LOCAL_VARIABLE,
                    "temp",
                    null,
                    "demo.Utility#run(String)",
                    null,
                    path,
                    false,
                    false)));

        assertEquals(Set.of("demo.Utility"), file.declaredQualifiedNames());
    }

    @Test
    public void rejectsBlankImportAndSymbolNames() {
        Path path = Path.of("src/main/java/demo/Utility.java");

        assertThrows(IllegalArgumentException.class,
            () -> new JavaProjectSemanticIndex.ImportDescriptor("   ", false, false));

        assertThrows(IllegalArgumentException.class, () -> new JavaProjectSemanticIndex.SymbolDescriptor(
            SymbolKind.CLASS,
            "   ",
            "demo.Utility",
            null,
            null,
            path,
            false,
            true));
    }
}
