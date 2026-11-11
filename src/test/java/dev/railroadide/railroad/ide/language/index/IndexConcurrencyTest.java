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

import static org.junit.jupiter.api.Assertions.*;

class IndexConcurrencyTest {
    @TempDir Path root;

    @Test
    void synchronousPreparationCannotCommitAfterCloseAndReopen() throws Exception {
        var service = new ProjectLanguageIndexService();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        service.registerIndexer(indexer);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var disk = new java.util.concurrent.atomic.AtomicReference<Index>();
        service.registerPersistence(new ProjectLanguageIndexPersistence<Index>() {
            public String languageId() { return "test"; }
            public Index loadIfCurrent(Path path) { return null; }
            public void save(Path path, Index index) { fail("Must use guarded save"); }
            public void save(Path path, Index index, java.util.function.Consumer<Runnable> commit) {
                if (writes.incrementAndGet() == 1) { entered.countDown(); TestIndexer.await(release); }
                commit.accept(() -> disk.set(index));
            }
            public void delete(Path path) {}
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
            } finally { release.countDown(); }
        }
    }

    @Test
    void persistenceFailureDoesNotDiscardPublishedMemory() {
        var service = new ProjectLanguageIndexService();
        var indexer = new TestIndexer(); indexer.release.countDown();
        service.registerIndexer(indexer);
        service.registerPersistence(new ProjectLanguageIndexPersistence<Index>() {
            public String languageId() { return "test"; }
            public Index loadIfCurrent(Path path) { return null; }
            public void save(Path path, Index index) {}
            public void save(Path path, Index index, java.util.function.Consumer<Runnable> commit) {
                throw new java.io.UncheckedIOException(new java.io.IOException("test disk failure"));
            }
            public void delete(Path path) {}
        });
        var context = context(service);
        assertSame(service.index(context, "test"), service.current(context, "test"));
    }

    @Test
    void concurrentColdCallsShareOneConstruction() throws Exception {
        var service = new ProjectLanguageIndexService();
        var indexer = new TestIndexer();
        service.registerIndexer(indexer);
        var context = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.index(context, "test"));
            assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> service.index(context, "test"));
            indexer.release.countDown();
            assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, indexer.builds.get());
        }
    }

    @Test
    void invalidationDoesNotWaitForConstructionAndRejectsItsResult() throws Exception {
        var service = new ProjectLanguageIndexService();
        var indexer = new TestIndexer();
        service.registerIndexer(indexer);
        var context = context(service);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var old = workers.submit(() -> service.index(context, "test"));
            assertTrue(indexer.entered.await(5, TimeUnit.SECONDS));
            try {
                workers.submit(() -> service.invalidateProject(context.project())).get(5, TimeUnit.SECONDS);
                assertNull(service.current(context, "test"));
            } finally { indexer.release.countDown(); }
            assertInstanceOf(CancellationException.class, assertThrows(ExecutionException.class,
                () -> old.get(5, TimeUnit.SECONDS)).getCause());
            assertNull(service.current(context, "test"));
        }
    }

    @Test
    void closedSessionCannotPublishIntoReopenedPath() throws Exception {
        var service = new ProjectLanguageIndexService();
        var indexer = new TestIndexer();
        service.registerIndexer(indexer);
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
            } finally { indexer.release.countDown(); }
        }
    }

    @Test
    void concurrentFileChangesApplyToLatestPredecessor() throws Exception {
        var service = new ProjectLanguageIndexService();
        var indexer = new TestIndexer();
        indexer.release.countDown();
        service.registerIndexer(indexer);
        var context = context(service);
        service.index(context, "test");
        Path a = root.resolve("A.test"), b = root.resolve("B.test");
        Files.writeString(a, "A"); Files.writeString(b, "B");
        indexer.blockFile = a;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.updateFile(context, "test", a));
            assertTrue(indexer.fileEntered.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> service.updateFile(context, "test", b));
            indexer.fileRelease.countDown();
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
            assertNotNull(service.current(context, "test").getFileIndex(a));
            assertNotNull(service.current(context, "test").getFileIndex(b));
        } finally { indexer.fileRelease.countDown(); }
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

    private record FileEntry(Path path) implements LanguageFileIndex {}
    private record Index(Map<Path, FileEntry> files) implements ProjectLanguageIndex<FileEntry> {
        public String languageId() { return "test"; }
        public FileEntry getFileIndex(Path path) { return files.get(path); }
    }
    private static class TestIndexer implements ProjectLanguageIndexer<Index, FileEntry> {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CountDownLatch fileEntered = new CountDownLatch(1), fileRelease = new CountDownLatch(1);
        final AtomicInteger builds = new AtomicInteger();
        volatile Path blockFile;
        public String languageId() { return "test"; }
        public boolean supports(Path file) { return true; }
        public Index build(ProjectIndexContext context, Collection<Path> files) {
            builds.incrementAndGet(); entered.countDown(); await(release);
            return new Index(Map.of());
        }
        public FileEntry indexFile(ProjectIndexContext context, Path file, String source) {
            if (file.equals(blockFile)) { fileEntered.countDown(); await(fileRelease); }
            return new FileEntry(file);
        }
        public Index withUpdatedFile(Index index, Path file, FileEntry entry) {
            var files = new HashMap<>(index.files()); files.put(file, entry); return new Index(Map.copyOf(files));
        }
        public Index withRemovedFile(Index index, Path file) {
            var files = new HashMap<>(index.files()); files.remove(file); return new Index(Map.copyOf(files));
        }
        private static void await(CountDownLatch latch) {
            try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
        }
    }
}
