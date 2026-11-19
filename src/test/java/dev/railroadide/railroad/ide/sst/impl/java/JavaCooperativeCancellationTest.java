package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCooperativeCancellationTest {
    @TempDir
    public Path directory;

    @Test
    public void cancellationStopsSemanticTraversalAfterParsingHasCompleted() {
        var tree = JavaSyntaxParser.parse(largeSource());
        var checks = new AtomicInteger();
        var failure = new CancellationException("cancel semantic traversal");
        assertSame(failure, assertThrows(CancellationException.class,
            () -> JavaSemanticAnalyzer.analyzeFacts(tree, JavaProjectSemanticIndex.empty(), () -> {
                if (checks.incrementAndGet() == 20)
                    throw failure;
            })));
        assertEquals(20, checks.get());
        assertNotNull(JavaSemanticAnalyzer.analyzeFacts(tree, JavaProjectSemanticIndex.empty()));
    }

    @Test
    public void semanticPassesUseTheOriginalDeadline() {
        var tree = JavaSyntaxParser.parse(largeSource());
        var clock = new AtomicLong();
        Deadline deadline = Deadline.after(Duration.ofNanos(12), clock::getAndIncrement);
        var document = tree.documentSnapshot();
        var context = new FeatureRequestContext(new WorkspaceSnapshot("test", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 1), document, CancellationToken.none(), deadline);
        assertThrows(DeadlineExceededException.class,
            () -> JavaSemanticAnalyzer.analyzeFacts(tree, JavaProjectSemanticIndex.empty(), context::checkActive));
        assertSame(deadline, context.deadline());
        assertTrue(clock.get() >= 12);
    }

    @Test
    public void sourceIndexingStopsDuringExtractionAndCanRetry() {
        var indexer = new JavaProjectSemanticIndexer();
        var checks = new AtomicInteger();
        var failure = new CancellationException("cancel source index");
        Path file = directory.resolve("Large.java");
        assertSame(failure, assertThrows(CancellationException.class,
            () -> indexer.indexFile(file, largeSource(), () -> {
                if (checks.incrementAndGet() == 24)
                    throw failure;
            })));
        var complete = indexer.indexFile(file, "class Large { int value; }");
        assertEquals(2, complete.declaredSymbols().size());
    }

    @Test
    public void binaryCancellationPropagatesWithoutBeingLoggedAsABadClass() throws Exception {
        Path archive = archive();
        var checks = new AtomicInteger();
        var failure = new CancellationException("cancel class publication");
        assertSame(failure, assertThrows(CancellationException.class,
            () -> JavaLibrarySymbolIndex.build(List.of(archive), () -> {
                if (checks.incrementAndGet() == 12)
                    throw failure;
            })));
        assertEquals(12, checks.get());
        assertEquals(32, JavaLibrarySymbolIndex.build(List.of(archive)).declaredQualifiedNames().size());
        Files.delete(archive);
        assertFalse(Files.exists(archive));
    }

    @Test
    public void binaryAndSourceEntryPointsHonorWorkerInterruption() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> JavaLibrarySymbolIndex.build(List.of()));
            assertThrows(CancellationException.class, () -> JavaJdkSymbolIndex.build(null));
            assertThrows(CancellationException.class,
                () -> new JavaProjectSemanticIndexer().indexFile(directory.resolve("A.java"), "class A {}"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void interruptedIncrementalParsingAndReusePlanningDoNotFallBack() {
        String original = "class A { int value = 1; }";
        String updated = original.replace('1', '2');
        var tree = JavaSyntaxParser.parse(original);
        var edit = new JavaSyntaxParser.TextEdit(original.indexOf('1'), 1, "2");
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,
                () -> JavaSyntaxParser.parseIncremental(tree, original, updated, edit));
            assertThrows(CancellationException.class,
                () -> JavaSyntaxParser.planReuse(tree, original, updated, edit));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(updated, JavaSyntaxParser.parseIncremental(tree, original, updated, edit)
            .tree().documentSnapshot().text());
    }

    @Test
    public void suppliedLexerRemainsCallerOwnedWhenParsingIsInterrupted() {
        var snapshot = JavaSyntaxParser.parse("class A {}").documentSnapshot();
        var closed = new AtomicBoolean();
        var lexer = new JavaLexer(snapshot.text()) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> JavaSyntaxParser.parse(lexer));
            assertThrows(CancellationException.class, () -> JavaSyntaxParser.parse(snapshot, lexer));
            assertFalse(closed.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        try {
            assertEquals(snapshot.text(), JavaSyntaxParser.parse(snapshot, lexer).documentSnapshot().text());
            assertFalse(closed.get());
        } finally {
            lexer.close();
        }
        assertTrue(closed.get());
    }

    @Test
    public void cancelledCompletionDoesNotReturnPartialSuggestions() {
        String source = largeSource().replace("}", "void run(){ this. } }");
        int trigger = source.indexOf("this.") + 4;
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class,
            () -> JavaSemanticCompletionEngine.compute(source, trigger, JavaProjectSemanticIndex.empty(), () -> {
                if (checks.incrementAndGet() == 32)
                    throw new CancellationException();
            }));
        assertEquals(32, checks.get());
    }

    @Test
    public void activeRequestPreservesItsDocumentIdentityAndExistingSemantics() {
        var tree = JavaSyntaxParser.parse("class A { int value = 1; int read(){ return value; } }");
        var document = tree.documentSnapshot();
        var context = new FeatureRequestContext(new WorkspaceSnapshot("test", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 1), document, CancellationToken.none(), Deadline.none());
        var model = JavaSemanticAnalyzer.analyzeFacts(context, JavaProjectSemanticIndex.empty());
        assertSame(document, model.syntaxTree().documentSnapshot());
        assertEquals(JavaSemanticAnalyzer.analyzeFacts(tree, JavaProjectSemanticIndex.empty()).diagnostics(),
            model.diagnostics());
        assertEquals(1, model.rootScope().lookupNearest("A").size());
    }

    private Path archive() throws Exception {
        Path archive = directory.resolve("classes.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
            for (int index = 0; index < 32; index++) {
                String name = "sample/Type" + index;
                var writer = new ClassWriter(0);
                writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
                writer.visitEnd();
                output.putNextEntry(new JarEntry(name + ".class"));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
        }
        return archive;
    }

    private static String largeSource() {
        var source = new StringBuilder("class Large { ");
        for (int index = 0; index < 1200; index++) {
            source.append("int field").append(index).append(" = ").append(index).append(';');
        }
        return source.append('}').toString();
    }
}
