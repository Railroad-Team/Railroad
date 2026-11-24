package dev.railroadide.railroad.ide.sst.project;

import com.google.gson.Gson;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class JavaLibrarySymbolIndexTest {
    private static final Runnable ACTIVE = () -> {
    };

    @TempDir
    public Path temp;

    @Test
    public void countsUniqueRootsAndStopsReportingScanProgressWhileMerging() throws IOException {
        var registry = new BackgroundActivities();
        var samples = new ConcurrentLinkedQueue<BackgroundActivities.Activity>();
        Path first = jar("one.jar", "sample/One", "one");
        Path second = jar("two.jar", "sample/Two", "two");
        try (var activity = registry.start("index");
            var observation = registry.observe(() -> samples.add(registry.running().getFirst()))) {
            ScopedValue.where(ActivityProgress.CURRENT, activity)
                .run(() -> JavaLibrarySymbolIndex.build(List.of(first, second, first), ACTIVE, null));
            assertTrue(samples.stream().anyMatch(sample -> sample.total() == 2 && sample.completed() == 2));
            assertEquals("railroad.activity.finalizing", registry.running().getFirst().phase());
            assertEquals(-1, registry.running().getFirst().fraction());
        }
    }

    @Test
    public void cachedBuildsMatchUncachedBuild() throws Exception {
        List<Path> roots = List.of(JavaLibraryStubCacheTest.jarOf(ClassReader.class),
            JavaLibraryStubCacheTest.jarOf(Gson.class));
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));

        JavaLibrarySymbolIndex uncached = JavaLibrarySymbolIndex.build(roots, ACTIVE, null);
        JavaLibrarySymbolIndex populating = JavaLibrarySymbolIndex.build(roots, ACTIVE, cache);
        JavaLibrarySymbolIndex restored = JavaLibrarySymbolIndex.build(roots, ACTIVE,
            new JavaLibraryStubCache(temp.resolve("cache")));

        for (JavaLibrarySymbolIndex index : List.of(populating, restored)) {
            assertEquals(List.copyOf(uncached.declaredQualifiedNames()), List.copyOf(index.declaredQualifiedNames()));
            assertEquals(uncached.classStubsByQualifiedName(), index.classStubsByQualifiedName());
            assertEquals(uncached.binaryLocations(), index.binaryLocations());
        }
    }

    @Test
    public void concurrentOverlappingClasspathsShareArchiveEntriesAndKeepPrecedence() throws Exception {
        Path shared = jar("shared.jar", "sample/Shared", "sharedMethod");
        Path first = jar("first.jar", "sample/Duplicate", "firstMethod");
        Path second = jar("second.jar", "sample/Duplicate", "secondMethod");
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));
        var start = new CyclicBarrier(2);
        var classpaths = List.of(List.of(first, shared, second), List.of(second, temp.resolve("./shared.jar"), first));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var indexes = classpaths.stream().map(roots -> workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return JavaLibrarySymbolIndex.build(roots, ACTIVE, cache);
            })).toList();
            var left = indexes.getFirst().get(10, TimeUnit.SECONDS);
            var right = indexes.getLast().get(10, TimeUnit.SECONDS);
            assertNotNull(left.lookupClassStub("sample.Shared"));
            assertSame(left.lookupClassStub("sample.Shared"), right.lookupClassStub("sample.Shared"));
            assertSame(left.binaryLocations().get("sample.Shared"), right.binaryLocations().get("sample.Shared"));
            assertEquals("firstMethod", left.lookupClassStub("sample.Duplicate").methods().getFirst().name());
            assertEquals("secondMethod", right.lookupClassStub("sample.Duplicate").methods().getFirst().name());
            assertEquals(JavaLibrarySymbolIndex.archiveEntry(second, "sample/Duplicate.class"),
                right.binaryLocations().get("sample.Duplicate"));
        }
    }

    @Test
    public void cancelledArchiveLoadCanBeRetriedWithTheSameCache() throws IOException {
        var archive = jar("shared.jar", "sample/Shared", "sharedMethod");
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));
        ActivityProgress progress = (phase, unit, initial, total) -> completed -> {
            if (phase.equals("railroad.activity.archive_entries") && completed == 1)
                throw new CancellationException("cancel archive load");
        };
        assertThrows(CancellationException.class, () -> ScopedValue.where(ActivityProgress.CURRENT, progress)
            .run(() -> JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache)));
        var retry = JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache);
        assertEquals("sharedMethod", onlyMethod(retry));
    }

    @Test
    public void partiallyShadowedArchivesRetainOnlyVisibleStubsAndCanRestoreOthers() throws Exception {
        var first = jar("first.jar", "sample/Duplicate", "winner");
        var second = jar("second.jar", Map.of("sample/Duplicate", "fallback", "sample/Other", "other"));
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));
        var combined = JavaLibrarySymbolIndex.build(List.of(first, second), ACTIVE, cache);
        var shadowed = unmaskedStub(second, cache);
        for (int i = 0; i < 40 && !shadowed.refersTo(null); i++) {
            System.gc();
            Thread.sleep(25);
        }
        assertTrue(shadowed.refersTo(null), "The combined index retained a shadowed stub");
        var unmasked = JavaLibrarySymbolIndex.build(List.of(second), ACTIVE, cache);
        assertEquals("fallback", unmasked.lookupClassStub("sample.Duplicate").methods().getFirst().name());
        assertEquals("winner", combined.lookupClassStub("sample.Duplicate").methods().getFirst().name());
        assertSame(combined.lookupClassStub("sample.Other"), unmasked.lookupClassStub("sample.Other"));
        assertEquals(JavaLibrarySymbolIndex.archiveEntry(second, "sample/Duplicate.class"),
            unmasked.binaryLocations().get("sample.Duplicate"));
    }

    private static WeakReference<ClassStub> unmaskedStub(Path archive, JavaLibraryStubCache cache) {
        var index = JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache);
        return new WeakReference<>(index.lookupClassStub("sample.Duplicate"));
    }

    @Test
    public void earlierRootsKeepPrecedence() throws IOException {
        Path first = jar("first.jar", "sample/Duplicate", "fromFirst");
        Path second = jar("second.jar", "sample/Duplicate", "fromSecond");
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));

        for (JavaLibraryStubCache usedCache : new JavaLibraryStubCache[]{null, cache, cache}) {
            assertEquals("fromFirst", onlyMethod(JavaLibrarySymbolIndex.build(List.of(first, second), ACTIVE,
                usedCache)));
            assertEquals("fromSecond", onlyMethod(JavaLibrarySymbolIndex.build(List.of(second, first), ACTIVE,
                usedCache)));
        }
    }

    @Test
    public void changedArchiveIsRescanned() throws IOException {
        Path archive = jar("library.jar", "sample/Changing", "before");
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));
        var original = JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache);

        jar("library.jar", "sample/Changing", "afterTheChange");

        var updated = JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache);
        assertEquals("afterTheChange", onlyMethod(updated));
        assertEquals("before", onlyMethod(original));
        assertNotSame(original.lookupClassStub("sample.Changing"), updated.lookupClassStub("sample.Changing"));
    }

    @Test
    public void cancellationStopsEveryWorker() throws IOException {
        List<Path> roots = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            roots.add(jar("library" + index + ".jar", "sample/Type" + index, "method"));
        }
        var checks = new AtomicInteger();

        assertThrows(CancellationException.class, () -> JavaLibrarySymbolIndex.build(roots, () -> {
            if (checks.incrementAndGet() == 2)
                throw new CancellationException("cancel scan");
        }, null));
        assertTrue(Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getName().equals("railroad-library-index"))
            .allMatch(thread -> waitForExit(thread)));
    }

    @Test
    public void archiveEntriesEncodeLikeUriPaths() throws Exception {
        Path archive = temp.resolve("with space").resolve("library.jar");
        for (String entry : List.of("sample/Box.class", "sample/Box$1.class", "classes/java/lang/Object.class",
            "with space/A.class", "ünïcode/B.class", "percent%/C.class", "hash#/D.class")) {
            String encoded = new URI(null, null, "/" + entry, null).getRawPath().substring(1);
            DocumentUri expected = DocumentUri.parse(
                "jar:" + archive.toAbsolutePath().normalize().toUri().toASCIIString() + "!/" + encoded);

            assertEquals(expected, JavaLibrarySymbolIndex.archiveEntry(archive, entry), entry);
        }
    }

    private static boolean waitForExit(Thread thread) {
        try {
            thread.join(5000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return !thread.isAlive();
    }

    private static String onlyMethod(JavaLibrarySymbolIndex index) {
        return index.classStubsByQualifiedName().values().iterator().next().methods().getFirst().name();
    }

    private Path jar(String fileName, String internalName, String methodName) throws IOException {
        return jar(fileName, Map.of(internalName, methodName));
    }

    private Path jar(String fileName, Map<String, String> classes) throws IOException {
        Path archive = temp.resolve(fileName);
        try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
            for (var entry : classes.entrySet()) {
                var writer = new ClassWriter(0);
                writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, entry.getKey(), null,
                    "java/lang/Object", null);
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, entry.getValue(), "()V", null, null)
                    .visitEnd();
                writer.visitEnd();
                output.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
        }

        return archive;
    }
}
