package dev.railroadide.railroad.debug.source;

import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class LibrarySourceIndexTest {
    private static final String SOURCE = "package net.example;\r\n\r\npublic class Thing {\r\n}\r\n";

    @TempDir
    private Path directory;

    @Test
    public void resolvesNestedClassToMaterializedAttachedSource() throws Exception {
        Path jar = library("libs/thing-1.0.jar", true);
        var sources = new JavaLibrarySources(directory.resolve("views"));
        var index = new LibrarySourceIndex(List.of(jar), sources);

        Optional<DebugSource> resolved = index.resolve("net/example/Thing.java", "net.example.Thing$Inner");

        Path expected = sources.viewRoot(jar).resolve("net/example/Thing.java");
        assertEquals(Optional.of(new DebugSource.FileSource(expected)), resolved);
        assertEquals(SOURCE.replace("\r\n", "\n"), Files.readString(expected));
        assertEquals(List.of(sources.viewRoot(jar)), index.sourceRoots());
    }

    @Test
    public void acceptsPlatformSeparatorsInRuntimeSourcePath() throws Exception {
        Path jar = library("libs/thing-1.0.jar", true);
        var index = new LibrarySourceIndex(List.of(jar), new JavaLibrarySources(directory.resolve("views")));

        assertTrue(index.resolve("net" + File.separator + "example" + File.separator + "Thing.java",
            "net.example.Thing").isPresent());
    }

    @Test
    public void skipsJarWithoutSourcesForLaterJarWithSources() throws Exception {
        Path withoutSources = library("first/thing-1.0.jar", false);
        Path withSources = library("second/thing-2.0.jar", true);
        var sources = new JavaLibrarySources(directory.resolve("views"));
        var index = new LibrarySourceIndex(List.of(withoutSources, withSources), sources);

        assertEquals(
            Optional.of(new DebugSource.FileSource(sources.viewRoot(withSources).resolve("net/example/Thing.java"))),
            index.resolve("net/example/Thing.java", "net.example.Thing"));
    }

    @Test
    public void unknownClassesAndJarsWithoutSourcesResolveToNothing() throws Exception {
        Path jar = library("libs/thing-1.0.jar", false);
        var index = new LibrarySourceIndex(List.of(jar, directory.resolve("classes")),
            new JavaLibrarySources(directory.resolve("views")));

        assertTrue(index.resolve("net/example/Thing.java", "net.example.Thing").isEmpty());
        assertTrue(index.resolve("net/other/Missing.java", "net.other.Missing").isEmpty());
        assertEquals(1, index.sourceRoots().size());
    }

    @Test
    public void resolverMapsDependencyFilesToClassPatterns() throws Exception {
        Path jar = library("libs/thing-1.0.jar", true);
        var sources = new JavaLibrarySources(directory.resolve("views"));
        Files.createDirectories(directory.resolve("project/src/main/java"));
        SourceResolver resolver = ProjectSourceResolver.create(directory.resolve("project"),
            new LibrarySourceIndex(List.of(jar), sources));
        var dependencyFile = new DebugSource.FileSource(sources.viewRoot(jar).resolve("net/example/Thing.java"));
        var projectFile = new DebugSource.FileSource(directory.resolve("project/src/main/java/demo/Main.java"));

        assertTrue(resolver.isDependencySource(dependencyFile));
        assertFalse(resolver.isDependencySource(projectFile));
        assertEquals(Optional.of("net.example.Thing*"), resolver.classPatternFor(dependencyFile));
        assertEquals(Optional.of("demo.Main*"), resolver.classPatternFor(projectFile));
    }

    private Path library(String path, boolean withSources) throws IOException {
        Path jar = directory.resolve(path);
        Files.createDirectories(jar.getParent());
        zip(jar, Map.of(
            "net/example/Thing.class", new byte[]{(byte) 0xCA, (byte) 0xFE},
            "net/example/Thing$Inner.class", new byte[]{(byte) 0xCA, (byte) 0xFE}));
        if (withSources) {
            String name = jar.getFileName().toString();
            Path sources = jar.resolveSibling(name.substring(0, name.length() - ".jar".length()) + "-sources.jar");
            zip(sources, Map.of("net/example/Thing.java", SOURCE.getBytes(StandardCharsets.UTF_8)));
        }
        return jar;
    }

    private static void zip(Path file, Map<String, byte[]> entries) throws IOException {
        try (OutputStream output = Files.newOutputStream(file); var zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
}
