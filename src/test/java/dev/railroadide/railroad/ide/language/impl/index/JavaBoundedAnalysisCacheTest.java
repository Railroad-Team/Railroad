package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.project.JavaStubSymbolIndex;
import dev.railroadide.railroad.ide.sst.request.api.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class JavaBoundedAnalysisCacheTest {
    @Test
    public void coldWarmAndIncrementalCapturesReuseOnlyUnchangedInputs() {
        var loads = new AtomicInteger();
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            loads.incrementAndGet();
            return emptyIndex();
        }, 2, 3, 1)) {
            DefaultDocumentStore store = store();
            TextDocumentSnapshot original = source(DocumentId.create(), 0, "class Original {}");
            TextDocumentSnapshot stable = source(DocumentId.create(), 0, "class Stable {}");
            store.publishBacking(original);
            store.publishBacking(stable);
            WorkspaceSnapshot captured = store.capture();
            JavaWorkspaceAnalysis first = provider.capture(captured);
            JavaWorkspaceAnalysisProvider.CacheUsage cold = provider.cacheUsage();
            assertEquals(4, cold.misses());
            assertSame(first, provider.capture(captured));
            assertEquals(cold.misses(), provider.cacheUsage().misses());
            assertEquals(cold.hits() + 1, provider.cacheUsage().hits());

            store.publishOverlay(source(original.id(), 1, "class Changed {}"));
            JavaWorkspaceAnalysis updated = provider.capture(store.capture());
            assertTrue(updated.declaredQualifiedNames().contains("Changed"));
            assertFalse(updated.declaredQualifiedNames().contains("Original"));
            assertTrue(first.declaredQualifiedNames().contains("Original"));
            assertEquals(cold.misses() + 2, provider.cacheUsage().misses());
            assertEquals(1, loads.get());
        }
    }

    @Test
    public void bodyEditsReuseOtherFilesAndTypeNameChangesRebuildDependentDeclarations() {
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> emptyIndex())) {
            DefaultDocumentStore store = store();
            TextDocumentSnapshot type = source(DocumentId.create(), 0,
                "package api; class Value { int count() { return 0; } }");
            TextDocumentSnapshot client = source(DocumentId.create(), 0,
                "package app; import api.*; class Client { void use(Value v) {} }");
            store.publishBacking(type);
            store.publishBacking(client);
            JavaProjectSemanticIndex original = provider.capture(store.capture()).sourceIndex();
            Path clientPath = original.lookupQualifiedName("app.Client").getFirst().sourceFile();
            Path typePath = original.lookupQualifiedName("api.Value").getFirst().sourceFile();
            store.publishOverlay(source(type.id(), 1, type.text().replace("return 0", "return 1")));
            JavaProjectSemanticIndex edited = provider.capture(store.capture()).sourceIndex();
            assertSame(original.getFileIndex(clientPath), edited.getFileIndex(clientPath));
            assertNotSame(original.getFileIndex(typePath), edited.getFileIndex(typePath));

            store.publishOverlay(source(type.id(), 2, "package api; class Other {}"));
            JavaProjectSemanticIndex renamed = provider.capture(store.capture()).sourceIndex();
            assertNotSame(edited.getFileIndex(clientPath), renamed.getFileIndex(clientPath));
            assertFalse(renamed.declaredQualifiedNames().contains("api.Value"));
            assertTrue(renamed.declaredQualifiedNames().contains("api.Other"));
            assertTrue(original.declaredQualifiedNames().contains("api.Value"));
        }
    }

    @Test
    public void revisionAndConfigurationChurnRemainBoundedAndEvictedResultsStayUsable() {
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> emptyIndex(), 2, 3, 1)) {
            DefaultDocumentStore store = store();
            var id = DocumentId.create();
            store.publishBacking(source(id, 0, "class First {}"));
            JavaWorkspaceAnalysis retained = provider.capture(store.capture());
            for (int revision = 1; revision <= 40; revision++) {
                store.publishOverlay(source(id, revision, "class Revision" + revision + " {}"));
                store.publishConfiguration(new WorkspaceConfigurationSnapshot(revision, Set.of()));
                provider.capture(store.capture());
                JavaWorkspaceAnalysisProvider.CacheUsage usage = provider.cacheUsage();
                assertTrue(usage.workspaces() <= 2);
                assertTrue(usage.sourceFiles() <= 3);
                assertTrue(usage.binaries() <= 1);
            }

            assertTrue(provider.cacheUsage().evictions() > 0);
            assertTrue(retained.declaredQualifiedNames().contains("First"));
            provider.clearCaches();
            assertEmpty(provider);
            assertTrue(provider.capture(store.capture()).declaredQualifiedNames().contains("Revision40"));
        }
    }

    @Test
    public void closeDoesNotWaitForLoaderAndLateResultsCannotRepopulateCaches() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            entered.countDown();
            await(release);
            return emptyIndex();
        });

        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<JavaWorkspaceAnalysis> result = workers.submit(() -> provider.capture(store().capture()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                workers.submit(provider::close).get(5, TimeUnit.SECONDS);
                assertEmpty(provider);
            } finally {
                release.countDown();
            }

            assertInstanceOf(CancellationException.class,
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS)).getCause());
            assertEmpty(provider);
            assertThrows(CancellationException.class, () -> provider.capture(store().capture()));
        }
    }

    @Test
    public void cancelledWaiterStopsWithoutWaitingForAnotherRequestsLoader() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var polled = new CountDownLatch(1);
        var cancellation = new CancellationTokenSource();
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            entered.countDown();
            await(release);
            return emptyIndex();
        });

            var workers = Executors.newFixedThreadPool(2)) {
            WorkspaceSnapshot capture = store().capture();
            Future<JavaWorkspaceAnalysis> first = workers.submit(() -> provider.capture(capture));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var context = new WorkspaceRequestContext(capture, () -> {
                polled.countDown();
                return cancellation.token().isCancellationRequested();
            }, Deadline.none());
            Future<JavaWorkspaceAnalysis> second = workers.submit(() -> provider.capture(context));
            try {
                assertTrue(polled.await(5, TimeUnit.SECONDS));
                cancellation.cancel();
                assertInstanceOf(CancellationException.class,
                    assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS)).getCause());
                assertFalse(first.isDone());
            } finally {
                release.countDown();
            }

            assertNotNull(first.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void coldCaptureOverlapsSourceIndexingWithBinaryLoading() {
        var binaryStarted = new CountDownLatch(1);
        var sourceIndexed = new CountDownLatch(1);
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            binaryStarted.countDown();
            await(sourceIndexed);
            return emptyIndex();
        })) {
            DefaultDocumentStore store = store();
            store.publishBacking(source(DocumentId.create(), 0, "class Example {}"));
            var context = new WorkspaceRequestContext(store.capture(), () -> {
                if (provider.cacheUsage().sourceFiles() == 1 && sourceIndexed.getCount() != 0) {
                    try {
                        await(binaryStarted);
                    } finally {
                        sourceIndexed.countDown();
                    }
                }

                return false;
            }, Deadline.none());

            assertTrue(provider.capture(context).declaredQualifiedNames().contains("Example"));
        }
    }

    @Test
    public void binaryLoadOutlivesACancelledRequestAndServesTheNext() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loads = new AtomicInteger();
        var cancellation = new CancellationTokenSource();
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            loads.incrementAndGet();
            entered.countDown();
            await(release);
            return emptyIndex();
        });

            var worker = Executors.newSingleThreadExecutor()) {
            WorkspaceSnapshot capture = store().capture();
            var context = new WorkspaceRequestContext(capture, cancellation.token(), Deadline.none());
            Future<JavaWorkspaceAnalysis> first = worker.submit(() -> provider.capture(context));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cancellation.cancel();
            assertInstanceOf(CancellationException.class,
                assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS)).getCause());
            release.countDown();

            assertNotNull(provider.capture(capture));
            assertNotNull(provider.capture(capture));
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void clearingDuringLoadRejectsPublicationButAllowsFreshRequests() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loads = new AtomicInteger();
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            if (loads.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
            }

            return emptyIndex();
        });

            var worker = Executors.newSingleThreadExecutor()) {
            WorkspaceSnapshot capture = store().capture();
            Future<JavaWorkspaceAnalysis> result = worker.submit(() -> provider.capture(capture));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            provider.clearCaches();
            release.countDown();
            assertInstanceOf(CancellationException.class,
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS)).getCause());
            assertEmpty(provider);
            assertNotNull(provider.capture(capture));
            assertEquals(2, loads.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void expiredRequestNeverLoadsAndZeroLimitsDisableRetention() {
        var loads = new AtomicInteger();
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> {
            loads.incrementAndGet();
            return emptyIndex();
        }, 0, 0, 0)) {
            DefaultDocumentStore store = store();
            store.publishBacking(source(DocumentId.create(), 0, "class Example {}"));
            WorkspaceSnapshot capture = store.capture();
            assertThrows(DeadlineExceededException.class, () -> provider.capture(new WorkspaceRequestContext(
                capture, CancellationToken.none(), Deadline.after(Duration.ZERO))));
            assertEquals(0, loads.get());
            assertNotSame(provider.capture(capture), provider.capture(capture));
            assertEquals(2, loads.get());
            assertEmpty(provider);
        }
    }

    private static DefaultDocumentStore store() {
        return new DefaultDocumentStore("cache-test", new WorkspaceConfigurationSnapshot(0, Set.of()));
    }

    private static TextDocumentSnapshot source(DocumentId id, long version, String text) {
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id),
            new DocumentVersion(version), "java", text, StandardCharsets.UTF_8);
    }

    private static JavaStubSymbolIndex emptyIndex() {
        return new JavaStubSymbolIndex(Map.of(), Map.of());
    }

    private static void assertEmpty(JavaWorkspaceAnalysisProvider provider) {
        JavaWorkspaceAnalysisProvider.CacheUsage usage = provider.cacheUsage();
        assertEquals(0, usage.workspaces());
        assertEquals(0, usage.sourceFiles());
        assertEquals(0, usage.binaries());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        }
    }
}
