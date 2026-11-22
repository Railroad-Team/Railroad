package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class JavaAttachedSourceAccessTest {
    @TempDir
    public Path directory;

    @Test
    public void attachedSourcesUseTheOwningModulesCompiledArtifact() throws IOException {
        Path api = apiJar("common/api.jar", Opcodes.ACC_PRIVATE);
        Path patched = apiJar("loader/api.jar", Opcodes.ACC_PUBLIC);
        var mod = new ClassWriter(0);
        mod.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Mod", null, "java/lang/Object", null);
        mod.visitEnd();
        Path binary = jar("1.0/" + "b".repeat(39) + "/mod.jar", Map.of("example/Mod.class", mod.toByteArray()));
        String source = "package example; public class Mod {}";
        String entry = "example/Mod.java";
        Path attached = jar("1.0/" + "c".repeat(40) + "/mod-sources.jar",
            Map.of(entry, source.getBytes(StandardCharsets.UTF_8)));
        var libraryDocument = document(JavaLibrarySymbolIndex.archiveEntry(attached, entry), source);
        Path consumerRoot = directory.resolve("consumer");
        var consumer = document(DocumentUri.fromPath(consumerRoot.resolve("Use.java")), "class Use {}");
        var config = new WorkspaceConfigurationSnapshot(1, Set.of("consumer", "libraryUser"),
            Map.of("module.consumer.sources", consumerRoot.toString(), "module.consumer.classpath", api.toString(),
                "module.libraryUser.classpath", patched + "\n" + binary));
        var workspace = new WorkspaceSnapshot("test",
            Map.of(libraryDocument.id(), libraryDocument, consumer.id(), consumer), config, 0);
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true,
            (roots, _) -> JavaLibrarySymbolIndex.build(roots))) {
            var view = provider.capture(workspace, libraryDocument.id());
            assertEquals(List.of(patched, binary), view.classpath());
            var type = view.lookupClassStub("api.Target");
            assertTrue(Modifier.isPublic(type.methods().getFirst().modifiers()));
            var project = provider.capture(workspace, consumer.id());
            assertTrue(Modifier.isPrivate(project.lookupClassStub("api.Target").methods().getFirst().modifiers()));
            assertFalse(project.declaredQualifiedNames().contains("example.Mod"));

            var sources = new JavaLibrarySources(directory.resolve("views"));
            var base = JavaLibrarySymbolIndex.build(List.of(binary));
            var opened = sources.open(base.binaryLocations().get("example.Mod"), base.lookupClassStub("example.Mod"),
                base.classStubsByQualifiedName()).orElseThrow();
            assertEquals(binary, sources.ownerArchive(opened, List.of(binary)).orElseThrow());
            assertEquals(binary,
                sources.ownerArchive(opened.uri().filePath().orElseThrow(), List.of(binary)).orElseThrow());
            assertTrue(sources.ownerArchive(opened, List.of(api)).isEmpty());
        }
    }

    private static TextDocumentSnapshot document(DocumentUri uri, String source) {
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), source, StandardCharsets.UTF_8);
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }

    private Path apiJar(String name, int access) throws IOException {
        var target = new ClassWriter(0);
        target.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "api/Target", null, "java/lang/Object", null);
        target.visitMethod(access, "render", "()V", null, null).visitEnd();
        target.visitEnd();
        return jar(name, Map.of("api/Target.class", target.toByteArray()));
    }
}
