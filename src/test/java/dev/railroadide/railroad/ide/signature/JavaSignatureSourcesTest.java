package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import dev.railroadide.railroad.ide.sst.document.api.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class JavaSignatureSourcesTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void initialize() {
        WorkspaceModes.initialize();
    }

    @Test
    public void closedRequestRemovesAllStagedSources() throws Exception {
        Path staged;
        try (JavaSignatureSources sources = JavaSignatureSources.create(analysis(false, false), directory)) {
            staged = sources.directory();
            assertEquals("package demo; class Dep { int first; }", Files.readString(staged.resolve("demo/Dep.java")));
        }
        assertFalse(Files.exists(staged));
    }

    @Test
    public void conflictingUnitsAreOmittedRatherThanOverwritten() throws Exception {
        try (JavaSignatureSources sources = JavaSignatureSources.create(analysis(true, false), directory)) {
            assertFalse(Files.exists(sources.directory().resolve("demo/Dep.java")));
        }
        assertEmpty();
    }

    @Test
    public void materializationFailureCleansTheRequestDirectory() throws Exception {
        assertThrows(NullPointerException.class, () -> JavaSignatureSources.create(analysis(false, true), directory));
        assertEmpty();
    }

    @Test
    public void cancelledRequestCreatesNoTemporaryTree() throws Exception {
        JavaWorkspaceAnalysis analysis = analysis(false, false);
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> JavaSignatureSources.create(analysis, directory));
        } finally {
            Thread.interrupted();
        }
        assertEmpty();
    }

    @Test
    public void cooperativeCancellationDuringStagingRemovesThePartialTree() throws Exception {
        JavaWorkspaceAnalysis analysis = analysis(false, false);
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class,
            () -> JavaSignatureSources.create(analysis, directory, Set.of(), () -> {
                if (checks.incrementAndGet() == 3)
                    throw new CancellationException();
            }));
        assertEquals(3, checks.get());
        assertEmpty();
    }

    @Test
    public void cancelledRequestCannotAcquireAWarmSourceLease() throws Exception {
        var request = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.fromPath(directory.resolve("Use.java")), DocumentVersion.initial(), "java",
            "package demo; class Use {}", StandardCharsets.UTF_8);
        var analysis = withRequest(request, "first");
        Path staged;
        try (var cache = new JavaSignatureSourceCache(1)) {
            try (var lease = cache.acquire(analysis, request)) {
                staged = lease.directory();
            }
            assertThrows(CancellationException.class, () -> cache.acquire(analysis, request, () -> {
                throw new CancellationException();
            }));
            assertTrue(Files.exists(staged));
        }
        assertFalse(Files.exists(staged));
    }

    @Test
    public void currentBufferEditsReuseDependenciesAndEvictionWaitsForReaders() throws Exception {
        var request = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.fromPath(directory.resolve("Use.java")), DocumentVersion.initial(), "java",
            "package demo; class Use {}", StandardCharsets.UTF_8);
        var cache = new JavaSignatureSourceCache(1);
        Path original;
        Path changed;
        try (JavaSignatureSourceCache.Lease first = cache.acquire(withRequest(request, "first"), request)) {
            original = first.directory();
            assertFalse(Files.exists(original.resolve("demo/Use.java")));
            var edited = new TextDocumentSnapshot(request.id(), request.uri(), request.version().next(),
                "java", "package demo; class Use { int edited; }", request.encoding());
            try (JavaSignatureSourceCache.Lease second = cache.acquire(withRequest(edited, "first"), edited)) {
                assertEquals(original, second.directory());
            }
            try (JavaSignatureSourceCache.Lease newer = cache.acquire(withRequest(edited, "changed"), edited)) {
                changed = newer.directory();
                assertNotEquals(original, changed);
                assertEquals("package demo; class Dep { int first; }",
                    Files.readString(original.resolve("demo/Dep.java")));
                cache.close();
                assertTrue(Files.exists(original));
                assertTrue(Files.exists(changed));
            }
            assertFalse(Files.exists(changed));
        } finally {
            cache.close();
        }
        assertFalse(Files.exists(original));
    }

    private JavaWorkspaceAnalysis withRequest(TextDocumentSnapshot request, String field) {
        var indexer = new JavaProjectSemanticIndexer();
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        JavaProjectSemanticIndex.SourceFileIndex use = indexer.indexFile(request.uri().filePath().orElseThrow(),
            request.text());
        String text = "package demo; class Dep { int " + field + "; }";
        JavaProjectSemanticIndex.SourceFileIndex dependency = indexer.indexFile(directory.resolve("Dep.java"), text);
        builder.putFile(use).putFile(dependency);
        JavaProjectSemanticIndex index = builder.build();
        return new JavaWorkspaceAnalysis(index, index, Map.of(use.path(), request.text(), dependency.path(), text),
            List.of(), Path.of(System.getProperty("java.home")));
    }

    @Test
    public void shutdownRemovesCachedAndEvictedTreesEvenAfterCloseWithActiveReaders() throws Exception {
        var request = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.fromPath(directory.resolve("Use.java")), DocumentVersion.initial(), "java",
            "package demo; class Use {}", StandardCharsets.UTF_8);
        var cache = new JavaSignatureSourceCache(1);
        try (JavaSignatureSourceCache.Lease evicted = cache.acquire(withRequest(request, "first"), request);
            JavaSignatureSourceCache.Lease cached = cache.acquire(withRequest(request, "second"), request)) {
            cache.close();
            assertTrue(Files.exists(evicted.directory()));
            assertTrue(Files.exists(cached.directory()));
            cache.shutdown();
            assertFalse(Files.exists(evicted.directory()));
            assertFalse(Files.exists(cached.directory()));
            assertThrows(CancellationException.class, () -> cache.acquire(withRequest(request, "third"), request));
            cache.shutdown();
        } finally {
            cache.shutdown();
        }
    }

    private void assertEmpty() throws Exception {
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    private JavaWorkspaceAnalysis analysis(boolean conflicting, boolean missingText) {
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        Map<Path, String> texts = new HashMap<>();
        var indexer = new JavaProjectSemanticIndexer();
        String first = "package demo; class Dep { int first; }";
        JavaProjectSemanticIndex.SourceFileIndex one = indexer.indexFile(directory.resolve("one/Dep.java"), first);
        builder.putFile(one);
        if (!missingText) {
            texts.put(one.path(), first);
        }
        if (conflicting) {
            String second = "package demo; class Dep { int second; }";
            JavaProjectSemanticIndex.SourceFileIndex two = indexer.indexFile(directory.resolve("two/Dep.java"), second);
            builder.putFile(two);
            texts.put(two.path(), second);
        }
        JavaProjectSemanticIndex sources = builder.build();
        return new JavaWorkspaceAnalysis(sources, sources, texts, List.of(), Path.of(System.getProperty("java.home")));
    }
}
