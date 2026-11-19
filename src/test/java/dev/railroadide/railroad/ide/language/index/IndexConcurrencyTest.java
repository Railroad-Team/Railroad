package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.impl.index.BasicLanguageIndexContext;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class IndexConcurrencyTest {
    @TempDir
    public Path root;

    @Test
    public void invalidationCooperativelyStopsAnActiveProvider() throws Exception {
        assertCooperativeStop("invalidate");
    }

    @Test
    public void configurationRefreshCooperativelyStopsAnActiveProvider() throws Exception {
        assertCooperativeStop("refresh");
    }

    @Test
    public void projectClosureCooperativelyStopsAnActiveProvider() throws Exception {
        assertCooperativeStop("close");
    }

    @Test
    public void pluginUnloadCooperativelyStopsAnActiveProvider() throws Exception {
        assertCooperativeStop("unload");
    }

    @Test
    public void pluginUnloadReleasesPublishedIndex() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        assertNotNull(service.index(context, "test"));
        IndexTestProviders.runtime(service).unregisterOwner("test");
        assertNull(service.current(context.project(), "test"));
        assertFalse(service.hasIndexer(context.project(), "test"));
        assertNull(service.index(context, "test"));
    }

    private void assertCooperativeStop(String action) throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer() {
            @Override
            public Index build(ProjectIndexContext context, Collection<Path> files) {
                entered.countDown();
                while (release.getCount() > 0) {
                    context.checkActive();
                    Thread.onSpinWait();
                }
                return new Index(Map.of());
            }
        };
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var task = workers.submit(() -> service.index(context, "test"));
            try {
                assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
                switch (action) {
                    case "invalidate" -> service.invalidate(context.project(), "test");
                    case "refresh" -> service.refreshProject(context.session());
                    case "close" -> service.closeProject(context.session());
                    case "unload" -> IndexTestProviders.runtime(service).unregisterOwner("test");
                    default -> throw new AssertionError(action);
                }
                var failure = assertThrows(ExecutionException.class, () -> task.get(5, TimeUnit.SECONDS));
                assertInstanceOf(CancellationException.class, failure.getCause());
                assertNull(service.current(context.project(), "test"));
            } finally {
                indexer.release.countDown();
            }
        }
    }

    @Test
    public void interruptingAWaitingCallerDoesNotCancelTheInitializer() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var owner = workers.submit(() -> service.index(context, "test"));
            assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
            var outcome = new CompletableFuture<Boolean>();
            var waiter = Thread.ofPlatform().unstarted(() -> {
                try {
                    service.index(context, "test");
                    outcome.completeExceptionally(new AssertionError("Expected interrupted acquisition cancellation"));
                } catch (CancellationException expected) {
                    outcome.complete(Thread.currentThread().isInterrupted());
                } catch (Throwable failure) {
                    outcome.completeExceptionally(failure);
                }
            });
            try {
                waiter.start();
                awaitQueued(context, waiter);
                waiter.interrupt();
                assertTrue(outcome.get(5, TimeUnit.SECONDS), "Interrupted acquisition restores the interrupt flag");
            } finally {
                indexer.release.countDown();
                waiter.join(5000);
            }
            assertNotNull(owner.get(5, TimeUnit.SECONDS));
            assertEquals(1, indexer.builds.get());
        } finally {
            indexer.release.countDown();
        }
    }

    @Test
    public void invalidationDuringSavePreparationRejectsTheFinalReplacement() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var commits = new AtomicInteger();
        IndexTestProviders.persistence(service, new ProjectLanguageIndexPersistence<Index>() {
            public String languageId() {
                return "test";
            }
            public Index loadIfCurrent(Path root) {
                return null;
            }
            public void save(Path root, Index index) {
                fail("Must use guarded replacement");
            }
            public void save(Path root, Index index, Consumer<Runnable> commit) {
                entered.countDown();
                TestIndexer.await(release);
                commit.accept(commits::incrementAndGet);
            }
            public void delete(Path root) {
            }
        });
        var context = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var save = workers.submit(() -> service.index(context, "test"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                workers.submit(() -> service.invalidateProject(context.project())).get(5, TimeUnit.SECONDS);
                assertNull(service.current(context, "test"));
            } finally {
                release.countDown();
            }
            assertThrows(ExecutionException.class, () -> save.get(5, TimeUnit.SECONDS));
            assertEquals(0, commits.get());
        }
    }

    @Test
    public void refreshedContextRejectsOldRebuildBeforeItCanInvalidateTheNewIndex() {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        var old = context(service);
        service.index(old, "test");
        service.refreshProject(old.session());
        var fresh = new ProjectIndexContext(old.project(), root, old.languages(), old.session());
        var current = service.index(fresh, "test");
        assertThrows(CancellationException.class, () -> service.rebuild(old, "test"));
        assertSame(current, service.current(fresh, "test"));
    }

    @Test
    public void initializationFailureAllowsASubsequentRetry() {
        var service = IndexTestProviders.service();
        var attempts = new AtomicInteger();
        var indexer = new TestIndexer() {
            public Index build(ProjectIndexContext context, Collection<Path> files) {
                if (attempts.incrementAndGet() == 1)
                    throw new IllegalStateException("test failure");
                return super.build(context, files);
            }
        };
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        assertThrows(IllegalStateException.class, () -> service.index(context, "test"));
        assertNull(service.current(context, "test"));
        assertNotNull(service.index(context, "test"));
        assertEquals(2, attempts.get());
    }

    @Test
    public void synchronousPreparationCannotCommitAfterCloseAndReopen() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var disk = new AtomicReference<Index>();
        IndexTestProviders.persistence(service, new ProjectLanguageIndexPersistence<Index>() {
            public String languageId() {
                return "test";
            }
            public Index loadIfCurrent(Path path) {
                return null;
            }
            public void save(Path path, Index index) {
                fail("Must use guarded save");
            }
            public void save(Path path, Index index, Consumer<Runnable> commit) {
                if (writes.incrementAndGet() == 1) {
                    entered.countDown();
                    TestIndexer.await(release);
                }
                commit.accept(() -> disk.set(index));
            }
            public void delete(Path path) {
            }
        });
        var oldContext = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var old = workers.submit(() -> service.index(oldContext, "test"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                assertNotNull(service.current(oldContext, "test"));
                workers.submit(() -> service.closeProject(oldContext.session())).get(5, TimeUnit.SECONDS);
                var fresh = context(service);
                var current = service.index(fresh, "test");
                release.countDown();
                assertThrows(ExecutionException.class, () -> old.get(5, TimeUnit.SECONDS));
                assertSame(current, disk.get());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void persistenceFailureDoesNotDiscardPublishedMemory() {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        IndexTestProviders.persistence(service, new ProjectLanguageIndexPersistence<Index>() {
            public String languageId() {
                return "test";
            }
            public Index loadIfCurrent(Path path) {
                return null;
            }
            public void save(Path path, Index index) {
            }
            public void save(Path path, Index index, Consumer<Runnable> commit) {
                throw new UncheckedIOException(new IOException("test disk failure"));
            }
            public void delete(Path path) {
            }
        });
        var context = context(service);
        assertSame(service.index(context, "test"), service.current(context, "test"));
    }

    @Test
    public void concurrentColdCallsShareOneConstruction() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.index(context, "test"));
            assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
            var second = new FutureTask<>(() -> service.index(context, "test"));
            var waiter = Thread.ofPlatform().unstarted(second);
            try {
                waiter.start();
                awaitQueued(context, waiter);
            } finally {
                indexer.release.countDown();
            }
            assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, indexer.builds.get());
        }
    }

    @Test
    public void invalidationDoesNotWaitForConstructionAndRejectsItsResult() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var old = workers.submit(() -> service.index(context, "test"));
            assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
            try {
                workers.submit(() -> service.invalidateProject(context.project())).get(5, TimeUnit.SECONDS);
                assertNull(service.current(context, "test"));
            } finally {
                indexer.release.countDown();
            }
            assertInstanceOf(CancellationException.class, assertThrows(ExecutionException.class,
                () -> old.get(5, TimeUnit.SECONDS)).getCause());
            assertNull(service.current(context, "test"));
        }
    }

    @Test
    public void closedSessionCannotPublishIntoReopenedPath() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        IndexTestProviders.indexer(service, indexer);
        var oldContext = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var old = workers.submit(() -> service.index(oldContext, "test"));
            assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
            try {
                workers.submit(() -> service.closeProject(oldContext.session())).get(5, TimeUnit.SECONDS);
                var fresh = context(service);
                indexer.release.countDown();
                var current = service.index(fresh, "test");
                assertThrows(ExecutionException.class, () -> old.get(5, TimeUnit.SECONDS));
                assertSame(current, service.current(fresh, "test"));
                assertThrows(CancellationException.class, () -> service.index(oldContext, "test"));
                service.closeProject(oldContext.session());
                assertSame(current, service.current(fresh, "test"));
            } finally {
                indexer.release.countDown();
            }
        }
    }

    @Test
    public void concurrentFileChangesApplyToLatestPredecessor() throws Exception {
        var service = IndexTestProviders.service();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        IndexTestProviders.indexer(service, indexer);
        var context = context(service);
        service.index(context, "test");
        Path a = root.resolve("A.test"), b = root.resolve("B.test");
        Files.writeString(a, "A");
        Files.writeString(b, "B");
        indexer.blockFile = a;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.updateFile(context, "test", a));
            assertTrue(indexer.fileEntered.await(5, TimeUnit.SECONDS));
            var second = new FutureTask<>(() -> service.updateFile(context, "test", b));
            var waiter = Thread.ofPlatform().unstarted(second);
            try {
                waiter.start();
                awaitQueued(context, waiter);
            } finally {
                indexer.fileRelease.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertNotNull(service.current(context, "test").getFileIndex(a));
            assertNotNull(service.current(context, "test").getFileIndex(b));
            assertNotNull(indexer.predecessors.get(b).getFileIndex(a), "B must derive from A's publication");
        } finally {
            indexer.fileRelease.countDown();
        }
    }

    // Inspect the existing lock's queue rather than adding a production scheduling hook.
    private static void awaitQueued(ProjectIndexContext context, Thread waiter) throws Exception {
        var entriesField = context.session().getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        var entries = (Map<?, ?>) entriesField.get(context.session());
        Object entry = entries.get("test");
        var operationField = entry.getClass().getDeclaredField("operation");
        operationField.setAccessible(true);
        var lock = (ReentrantLock) operationField.get(entry);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!lock.hasQueuedThread(waiter)) {
            if (!waiter.isAlive() || System.nanoTime() >= deadline) {
                fail("Caller did not reach the blocked operation lock");
            }
            Thread.onSpinWait();
        }
    }

    private ProjectIndexContext context(ProjectLanguageIndexService service) {
        Project project = (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPath" -> root;
                case "toString" -> root.toString();
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new ProjectIndexContext(project, root, Map.of("test",
            new BasicLanguageIndexContext("test", List.of(root), List.of(), List.of())), service.openProject(project));
    }

    private record FileEntry(Path path) implements LanguageFileIndex {
    }
    private record Index(Map<Path, FileEntry> files) implements ProjectLanguageIndex<FileEntry> {
        public String languageId() {
            return "test";
        }
        public FileEntry getFileIndex(Path path) {
            return files.get(path);
        }
    }
    private static class TestIndexer implements ProjectLanguageIndexer<Index, FileEntry> {
        protected final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        protected final CountDownLatch fileEntered = new CountDownLatch(1), fileRelease = new CountDownLatch(1);
        protected final AtomicInteger builds = new AtomicInteger();
        protected volatile Path blockFile;
        protected final Map<Path, Index> predecessors = new ConcurrentHashMap<>();
        public String languageId() {
            return "test";
        }
        public boolean supports(Path file) {
            return true;
        }
        public Index build(ProjectIndexContext context, Collection<Path> files) {
            builds.incrementAndGet();
            entered.countDown();
            await(release);
            return new Index(Map.of());
        }
        public FileEntry indexFile(ProjectIndexContext context, Path file, String source) {
            if (file.equals(blockFile)) {
                fileEntered.countDown();
                await(fileRelease);
            }
            return new FileEntry(file);
        }
        public Index withUpdatedFile(Index index, Path file, FileEntry entry) {
            predecessors.put(file, index);
            var files = new HashMap<>(index.files());
            files.put(file, entry);
            return new Index(Map.copyOf(files));
        }
        public Index withRemovedFile(Index index, Path file) {
            var files = new HashMap<>(index.files());
            files.remove(file);
            return new Index(Map.copyOf(files));
        }
        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("Latch timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException();
            }
        }
    }
}
