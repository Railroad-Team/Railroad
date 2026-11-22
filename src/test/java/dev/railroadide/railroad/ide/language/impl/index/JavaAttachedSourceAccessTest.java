package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreCallResolutionInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreImportInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreMemberResolutionInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreNameResolutionInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport;
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
    public void sourceOnlyClassAndItsFieldsBelongToTheOwningAttachment() throws IOException {
        String source = "package example; class Reader { int ok = Scope.START; int bad = Scope.MISSING; }";
        String scope = "package example; final class Scope { static final int START = 1; }";
        Path binary = jar("reader.jar", Map.of("example/Reader.class", libraryClass("example/Reader")));
        Path sibling = jar("other.jar", Map.of());
        Path archive = jar("reader-sources.jar", Map.of(
            "example/Reader.java", source.getBytes(StandardCharsets.UTF_8),
            "example/Scope.java", scope.getBytes(StandardCharsets.UTF_8)));
        var doc = document(JavaLibrarySymbolIndex.archiveEntry(archive, "example/Reader.java"), source);
        var config = new WorkspaceConfigurationSnapshot(1, Set.of("main"), Map.of(
            "module.main.classpath", binary + "\n" + sibling));
        var workspace = new WorkspaceSnapshot("attached", Map.of(doc.id(), doc), config, 0);
        var base = JavaLibrarySymbolIndex.build(List.of(binary, sibling));
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> base)) {
            var view = provider.capture(workspace, doc.id());
            var field = view.lookupMember("example.Scope", "START").getFirst();
            assertEquals(scope, view.sourceText(field.sourceFile()).orElseThrow());
            var diagnostics = JavaInspectionTestSupport.runProviders(List.of(new CoreNameResolutionInspection(),
                new CoreMemberResolutionInspection()), Path.of("Reader.java"), source, view);
            assertEquals(List.of("Cannot resolve member 'MISSING'"), diagnostics.stream()
                .map(issue -> issue.message()).toList());
        }
        assertTrue(base.lookupQualifiedName("example.Scope").isEmpty());
        assertTrue(base.forAttachedSource(binary, () -> {
        }).forAttachedSource(sibling, () -> {
        })
            .lookupQualifiedName("example.Scope").isEmpty());
    }

    @Test
    public void attachedImportsResolveToRelocatedBinaryDeclarationsOnlyInTheirOwnArchive() throws IOException {
        String source = """
            package example;
            import vendor.codec.api.Reader;
            import vendor.codec.util.Format;
            import unrelated.Other;
            public class Adapter {
                Reader reader;
                void use() { Format.name(); }
            }
            """;
        var adapter = new ClassWriter(0);
        adapter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Adapter", null, "java/lang/Object", null);
        adapter.visitField(0, "reader", "Linternal/codec/api/Reader;", null, null).visitEnd();
        adapter.visitEnd();
        Path binary = jar("adapter.jar", Map.of(
            "example/Adapter.class", adapter.toByteArray(),
            "internal/codec/api/Reader.class", libraryClass("internal/codec/api/Reader"),
            "internal/codec/util/Format.class", libraryClass("internal/codec/util/Format"),
            "internal/codec/util/Existing.class", libraryClass("internal/codec/util/Existing"),
            "internal/Other.class", libraryClass("internal/Other")));
        jar("adapter-sources.jar", Map.of("example/Adapter.java", source.getBytes(StandardCharsets.UTF_8)));
        Path separate = jar("separate.jar", Map.of(
            "vendor/codec/util/Existing.class", libraryClass("vendor/codec/util/Existing"),
            "internal/codec/util/Foreign.class", libraryClass("internal/codec/util/Foreign")));
        var base = JavaLibrarySymbolIndex.build(List.of(binary, separate));
        var view = base.forAttachedSource(binary, () -> {
        });
        assertEquals("internal.codec.api.Reader", view.binaryTypeName("vendor.codec.api.Reader"));
        assertEquals(base.lookupQualifiedName("internal.codec.util.Format"),
            view.lookupQualifiedName("vendor.codec.util.Format"));
        assertEquals(base.lookupMember("internal.codec.util.Format", "name"),
            view.lookupMember("vendor.codec.util.Format", "name"));
        assertFalse(view.lookupMember("vendor.codec.util.Format", "name").isEmpty());
        assertTrue(view.containsPackage("vendor.codec.api"));
        assertTrue(view.lookupQualifiedName("unrelated.Other").isEmpty());
        assertTrue(view.lookupQualifiedName("vendor.codec.util.Foreign").isEmpty());
        assertEquals("vendor.codec.util.Existing", view.binaryTypeName("vendor.codec.util.Existing"));
        assertNull(base.lookupClassStub("vendor.codec.api.Reader"));
        assertNull(base.forAttachedSource(separate, () -> {
        }).lookupClassStub("vendor.codec.api.Reader"));
        assertNull(view.forAttachedSource(separate, () -> {
        }).lookupClassStub("vendor.codec.api.Reader"));
        var diagnostics = JavaInspectionTestSupport.runProviders(List.of(new CoreImportInspection(),
            new CoreCallResolutionInspection()), Path.of("Adapter.java"), source, view);
        assertEquals(List.of("Cannot resolve import 'unrelated.Other'"), diagnostics.stream()
            .filter(issue -> issue.code().startsWith("SEM_UNRESOLVED_")).map(issue -> issue.message()).toList());
    }

    @Test
    public void conflictingRelocationEvidenceDoesNotIntroducePackageAliases() throws IOException {
        var adapter = new ClassWriter(0);
        adapter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Adapter", null, "java/lang/Object", null);
        adapter.visitField(0, "first", "Lone/codec/Reader;", null, null).visitEnd();
        adapter.visitField(0, "second", "Ltwo/codec/Writer;", null, null).visitEnd();
        adapter.visitEnd();
        Path binary = jar("ambiguous.jar", Map.of(
            "example/Adapter.class", adapter.toByteArray(),
            "one/codec/Reader.class", libraryClass("one/codec/Reader"),
            "two/codec/Writer.class", libraryClass("two/codec/Writer")));
        String source = """
            package example;
            import vendor.codec.Reader;
            import vendor.codec.Writer;
            class Adapter { Reader first; Writer second; }
            """;
        jar("ambiguous-sources.jar", Map.of("example/Adapter.java", source.getBytes(StandardCharsets.UTF_8)));
        var base = JavaLibrarySymbolIndex.build(List.of(binary));
        assertSame(base, base.forAttachedSource(binary, () -> {
        }));
    }

    private static byte[] libraryClass(String name) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "name",
            "()Ljava/lang/String;", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

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
            assertTrue(Modifier.isPublic(type.fields().getFirst().modifiers()));
            assertTrue(Modifier.isPrivate(type.fields().getLast().modifiers()));
            var project = provider.capture(workspace, consumer.id());
            assertTrue(Modifier.isPrivate(project.lookupClassStub("api.Target").methods().getFirst().modifiers()));
            assertTrue(Modifier.isPrivate(project.lookupClassStub("api.Target").fields().getFirst().modifiers()));
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
        target.visitMethod(Opcodes.ACC_PRIVATE, "render", "(I)V", null, null).visitEnd();
        target.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(I)V", null, null).visitEnd();
        target.visitField(access, "value", "I", null, null).visitEnd();
        target.visitField(Opcodes.ACC_PRIVATE, "other", "I", null, null).visitEnd();
        target.visitEnd();
        return jar(name, Map.of("api/Target.class", target.toByteArray()));
    }
}
