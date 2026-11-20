package dev.railroadide.railroad.ide.sst.project;

import com.google.gson.Gson;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

public class JavaLibraryStubCacheTest {
    @TempDir
    public Path temp;

    private JavaLibraryStubCache cache;
    private Path archive;

    @BeforeEach
    public void setUp() throws IOException {
        cache = new JavaLibraryStubCache(temp.resolve("cache"));
        archive = Files.writeString(temp.resolve("library.jar"), "jar");
    }

    @Test
    public void realArchivesRoundTrip() throws Exception {
        for (Class<?> type : List.of(ClassReader.class, Gson.class)) {
            Path jar = jarOf(type);
            List<JavaLibraryStubCache.ArchiveClass> parsed = parseArchive(jar);
            assertFalse(parsed.isEmpty());

            cache.save(jar, 10, 20, parsed);

            assertEquals(parsed, cache.load(jar, 10, 20).orElseThrow(), jar.toString());
        }
    }

    @Test
    public void everyStubShapeRoundTrips() throws IOException {
        var nested = new AnnotationStub("sample.Nested", Map.of("value", "inner"));
        var annotation = new AnnotationStub("sample.Everything", Map.ofEntries(
            Map.entry("string", "text"),
            Map.entry("int", 1),
            Map.entry("long", 2L),
            Map.entry("float", 3.5f),
            Map.entry("double", 4.25),
            Map.entry("boolean", true),
            Map.entry("byte", (byte) 5),
            Map.entry("char", 'c'),
            Map.entry("short", (short) 6),
            Map.entry("type", org.objectweb.asm.Type.getType("Ljava/lang/String;")),
            Map.entry("list", List.of(1, 2, List.of("deep"))),
            Map.entry("nested", nested)));
        var typeVariable = new Type.TypeVariable("T");
        var generic = new Type.ClassType("java.util.Map", List.of(
            new Type.WildcardType(new Type.ClassType("java.lang.Number", List.of()), true),
            new Type.WildcardType(null, false),
            new Type.ArrayType(new Type.ArrayType(new Type.PrimitiveType("int")))));
        var parameter = new Parameter("value", typeVariable, List.of(annotation));
        var typeParameter = new TypeParameter("T", List.of(new Type.ClassType("java.lang.Comparable",
            List.of(typeVariable))));
        var stub = new ClassStub("sample", "Everything$Inner", List.of(typeParameter), null, List.of(generic),
            List.of(new FieldStub("field", generic, 9, List.of(annotation))),
            List.of(new MethodStub("method", typeVariable, List.of(parameter), 1,
                List.of(new Type.ClassType("java.io.IOException", List.of())), List.of(annotation),
                List.of(typeParameter))),
            List.of(new ConstructorStub(List.of(new Parameter(null, generic, List.of())), 2, List.of(),
                List.of(), List.of())),
            33, List.of(annotation), "sample.Everything");
        var classes = List.of(new JavaLibraryStubCache.ArchiveClass("sample/Everything$Inner.class", stub));

        cache.save(archive, 1, 2, classes);

        assertEquals(classes, cache.load(archive, 1, 2).orElseThrow());
    }

    @Test
    public void restoredStubsAreImmutable() throws IOException {
        var stub = new ClassStub("sample", "Box", List.of(), null, List.of(), List.of(),
            List.of(new MethodStub("get", new Type.PrimitiveType("int"), List.of(), 1, List.of(), List.of(),
                List.of())),
            List.of(), 1, List.of());
        cache.save(archive, 1, 2, List.of(new JavaLibraryStubCache.ArchiveClass("sample/Box.class", stub)));

        ClassStub restored = cache.load(archive, 1, 2).orElseThrow().getFirst().stub();

        assertThrows(UnsupportedOperationException.class, () -> restored.methods().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.interfaces().clear());
    }

    @Test
    public void changedArchiveIsNotReused() throws IOException {
        cache.save(archive, 1, 2, List.of());

        assertTrue(cache.load(archive, 1, 2).isPresent());
        assertEquals(Optional.empty(), cache.load(archive, 3, 2));
        assertEquals(Optional.empty(), cache.load(archive, 1, 4));
        assertEquals(Optional.empty(), cache.load(temp.resolve("other.jar"), 1, 2));
    }

    @Test
    public void truncatedCacheFailsToLoad() throws IOException {
        var stub = new ClassStub("sample", "Box", List.of(), null, List.of(), List.of(), List.of(), List.of(), 1,
            List.of());
        cache.save(archive, 1, 2, List.of(new JavaLibraryStubCache.ArchiveClass("sample/Box.class", stub)));
        try (var files = Files.list(temp.resolve("cache"))) {
            for (Path file : files.toList()) {
                byte[] bytes = Files.readAllBytes(file);
                Files.write(file, Arrays.copyOf(bytes, bytes.length - 3));
            }
        }

        assertThrows(IOException.class, () -> cache.load(archive, 1, 2));
    }

    @Test
    public void unpublishedAnnotationValuesAreRejected() {
        var annotation = new AnnotationStub("sample.Values", Map.of("ints", new int[]{1, 2}));
        var stub = new ClassStub("sample", "Box", List.of(), null, List.of(), List.of(), List.of(), List.of(), 1,
            List.of(annotation));

        assertThrows(IOException.class, () -> cache.save(archive, 1, 2,
            List.of(new JavaLibraryStubCache.ArchiveClass("sample/Box.class", stub))));
        assertEquals(List.of(), listCacheFiles());
    }

    private List<Path> listCacheFiles() {
        Path directory = temp.resolve("cache");
        if (Files.notExists(directory))
            return List.of();

        try (var files = Files.list(directory)) {
            return files.toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static List<JavaLibraryStubCache.ArchiveClass> parseArchive(Path jar) throws IOException {
        List<JavaLibraryStubCache.ArchiveClass> classes = new ArrayList<>();
        try (var jarFile = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(jarFile.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class") || name.endsWith("module-info.class")
                    || name.endsWith("package-info.class"))
                    continue;

                try (InputStream input = jarFile.getInputStream(entry)) {
                    classes.add(new JavaLibraryStubCache.ArchiveClass(name,
                        BinaryStubPublication.copy(ClassStubParser.parse(new ClassReader(input)))));
                }
            }
        }

        return classes;
    }

    public static Path jarOf(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
