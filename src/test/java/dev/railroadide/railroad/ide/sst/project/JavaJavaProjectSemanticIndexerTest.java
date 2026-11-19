package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

public class JavaJavaProjectSemanticIndexerTest {

    @TempDir
    public Path tempDir;

    @Test
    public void buildsProjectIndexAcrossMultipleFiles() throws Exception {
        Path root = tempDir.resolve("project");
        Path sourceRoot = root.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);

        Path aFile = sourceRoot.resolve("A.java");
        Path bFile = sourceRoot.resolve("B.java");

        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        Files.writeString(bFile, """
            package demo;

            import demo.A;
            import static demo.A.VALUE;

            class B {
                int use() {
                    return VALUE;
                }
            }
            """);

        var indexer = new JavaProjectSemanticIndexer();
        JavaProjectSemanticIndex index = indexer.build(root);

        assertEquals(2, index.files().size());
        assertTrue(index.getFile(aFile).isPresent());
        assertTrue(index.getFile(bFile).isPresent());
        assertEquals(2, index.getFilesByPackage("demo").size());
        assertEquals(1, index.lookupQualifiedName("demo.A").size());
        assertEquals(1, index.lookupQualifiedName("demo.B").size());
        assertEquals(1, index.lookupMember("demo.A", "VALUE").size());
    }

    @Test
    public void parallelBuildConfinesTheSuppliedCheckpointToItsCallingThread() {
        Thread owner = Thread.currentThread();
        var helperEntered = new CountDownLatch(1);
        var ownerEntered = new CountDownLatch(1);
        var checks = new AtomicInteger();
        var files = new ArrayList<Path>();
        for (int index = 0; index < 12; index++) {
            files.add(tempDir.resolve("Type" + index + ".java"));
        }
        var indexer = new JavaProjectSemanticIndexer() {
            @Override
            public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, Runnable activityCheck) {
                if (Thread.currentThread() == owner) {
                    ownerEntered.countDown();
                    await(helperEntered);
                } else {
                    helperEntered.countDown();
                    await(ownerEntered);
                }
                activityCheck.run();
                return entry(sourceFile);
            }
        };
        JavaProjectSemanticIndex index = indexer.build(files, new ActivityCheckpoint(() -> {
            assertSame(owner, Thread.currentThread());
            checks.incrementAndGet();
        }));
        assertEquals(Set.copyOf(files), index.files().keySet());
        assertTrue(checks.get() > files.size());
    }

    @Test
    public void cancellationDuringParallelExtractionStopsHelpersWithoutPublishingAnIndex() throws Exception {
        Thread owner = Thread.currentThread();
        var helperEntered = new CountDownLatch(1);
        var helperExited = new CountDownLatch(1);
        var helperThread = new AtomicReference<Thread>();
        var cancelBuild = new AtomicBoolean(true);
        var cancellation = new CancellationException("cancel active extraction");
        var published = new AtomicReference<JavaProjectSemanticIndex>();
        List<Path> files = List.of(tempDir.resolve("A.java"), tempDir.resolve("B.java"));
        var indexer = new JavaProjectSemanticIndexer() {
            @Override
            public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, Runnable activityCheck) {
                if (!cancelBuild.get())
                    return entry(sourceFile);
                if (Thread.currentThread() == owner) {
                    await(helperEntered);
                    activityCheck.run();
                } else {
                    helperThread.set(Thread.currentThread());
                    helperEntered.countDown();
                    try {
                        while (cancelBuild.get()) {
                            activityCheck.run();
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                        }
                    } finally {
                        helperExited.countDown();
                    }
                }
                return entry(sourceFile);
            }
        };
        try {
            assertSame(cancellation, assertThrows(CancellationException.class,
                () -> published.set(indexer.build(files, () -> {
                    assertSame(owner, Thread.currentThread());
                    if (helperEntered.getCount() == 0)
                        throw cancellation;
                }))));
            assertNull(published.get());
            assertTrue(helperExited.await(10, TimeUnit.SECONDS));
            helperThread.get().join(10_000);
            assertFalse(helperThread.get().isAlive());
        } finally {
            cancelBuild.set(false);
        }
        assertEquals(2, indexer.build(files).files().size());
    }

    @Test
    public void helperFailureStopsTheCallingExtractorAndPropagatesTheOriginalFailure() throws Exception {
        Thread owner = Thread.currentThread();
        var helperEntered = new CountDownLatch(1);
        var helperThread = new AtomicReference<Thread>();
        var failure = new IllegalStateException("failed extraction");
        var indexer = new JavaProjectSemanticIndexer() {
            @Override
            public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, Runnable activityCheck) {
                if (Thread.currentThread() != owner) {
                    helperThread.set(Thread.currentThread());
                    helperEntered.countDown();
                    throw failure;
                }
                await(helperEntered);
                while (true) {
                    activityCheck.run();
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> indexer.build(List.of(tempDir.resolve("A.java"), tempDir.resolve("B.java")))));
        helperThread.get().join(10_000);
        assertFalse(helperThread.get().isAlive());
    }

    private static JavaProjectSemanticIndex.SourceFileIndex entry(Path file) {
        return new JavaProjectSemanticIndex.SourceFileIndex(file, "demo", List.of(), List.of());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Extraction was interrupted");
        }
    }
}
