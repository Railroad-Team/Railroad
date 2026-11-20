package dev.railroadide.railroad.ide.sst.project;

import com.google.gson.Gson;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
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
    public void cachedBuildsMatchUncachedBuild() throws Exception {
        List<Path> roots = List.of(JavaLibraryStubCacheTest.jarOf(ClassReader.class),
            JavaLibraryStubCacheTest.jarOf(Gson.class));
        var cache = new JavaLibraryStubCache(temp.resolve("cache"));

        JavaLibrarySymbolIndex uncached = JavaLibrarySymbolIndex.build(roots, ACTIVE, null);
        JavaLibrarySymbolIndex populating = JavaLibrarySymbolIndex.build(roots, ACTIVE, cache);
        JavaLibrarySymbolIndex restored = JavaLibrarySymbolIndex.build(roots, ACTIVE, cache);

        for (JavaLibrarySymbolIndex index : List.of(populating, restored)) {
            assertEquals(List.copyOf(uncached.declaredQualifiedNames()), List.copyOf(index.declaredQualifiedNames()));
            assertEquals(uncached.classStubsByQualifiedName(), index.classStubsByQualifiedName());
            assertEquals(uncached.binaryLocations(), index.binaryLocations());
        }
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
        assertEquals("before", onlyMethod(JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache)));

        jar("library.jar", "sample/Changing", "afterTheChange");

        assertEquals("afterTheChange", onlyMethod(JavaLibrarySymbolIndex.build(List.of(archive), ACTIVE, cache)));
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
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, internalName, null, "java/lang/Object",
            null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, methodName, "()V", null, null).visitEnd();
        writer.visitEnd();

        Path archive = temp.resolve(fileName);
        try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new JarEntry(internalName + ".class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }

        return archive;
    }
}
