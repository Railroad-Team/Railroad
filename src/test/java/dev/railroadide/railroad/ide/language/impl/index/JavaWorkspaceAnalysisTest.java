package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.completion.*;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.signature.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class JavaWorkspaceAnalysisTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void initialize() {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
    }

    @Test
    public void delayedCompletionUsesCapturedUnsavedDependenciesAndInheritance() throws Exception {
        DefaultDocumentStore store = store();
        TextDocumentSnapshot parent = source("Parent.java",
            "package demo; public class Parent { public int original; }");
        TextDocumentSnapshot dependency = source("Dep.java", "package demo; public class Dep extends Parent {}");
        TextDocumentSnapshot use = source("Use.java", "package demo; class Use { void run(Dep d) { d. } }");
        store.publishBacking(parent);
        store.publishOverlay(next(parent, "package demo; public class Parent { public int captured; }"));
        store.publishBacking(dependency);
        store.publishBacking(use);
        Files.writeString(parent.uri().filePath().orElseThrow(),
            "package demo; public class Parent { public int diskOnly; }");
        WorkspaceSnapshot captured = store.capture();
        store.publishOverlay(next((TextDocumentSnapshot) store.current(parent.id()).orElseThrow(),
            "package demo; public class Parent { public int later; }"));
        Files.delete(parent.uri().filePath().orElseThrow());
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (_, method, _) -> {
                throw new AssertionError("Live project access: " + method.getName());
            });
        CompletionProvider provider = new JavaCompletionProvider(project);
        int trigger = use.text().indexOf("d.") + 1;
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            CompletionResult first = worker.submit(() -> provider.compute(captured, use.id(), trigger)).get(30,
                TimeUnit.SECONDS);
            assertNotNull(first);
            assertTrue(first.items().stream().anyMatch(item -> item.insertText().equals("captured")));
            assertFalse(first.items().stream()
                .anyMatch(item -> Set.of("original", "diskOnly", "later").contains(item.insertText())));
            CompletionResult current = provider.compute(store.capture(), use.id(), trigger);
            assertNotNull(current);
            assertTrue(current.items().stream().anyMatch(item -> item.insertText().equals("later")));
        }
    }

    @Test
    public void diagnosticsAndSignatureHelpResolveTheCapturedDependency() throws Exception {
        DefaultDocumentStore store = store();
        TextDocumentSnapshot dependency = source("Dep.java",
            "package demo; public class Dep { public int combine(int count, boolean enabled) { return count; } }");
        TextDocumentSnapshot use = source("Use.java",
            "package demo; class Use { void run(Dep d) { d.combine(1, true); } }");
        store.publishBacking(dependency);
        store.publishBacking(use);
        WorkspaceSnapshot captured = store.capture();
        store.publishOverlay(next(dependency, "package demo; public class Dep {}"));
        Files.writeString(dependency.uri().filePath().orElseThrow(), "package demo; public class Dep {}\n");
        var diagnostics = new JavaDiagnosticsProvider();
        assertTrue(diagnostics.compute(captured, use.id()).stream()
            .noneMatch(diagnostic -> diagnostic.code().equals("SEM_UNRESOLVED_CALL")));
        assertTrue(diagnostics.compute(store.capture(), use.id()).stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEM_UNRESOLVED_CALL")));
        SignatureHelpProvider signatures = new JdtJavaSignatureHelpProvider(new String[0]);
        SignatureHelp help = signatures.compute(captured, use.id(), use.text().indexOf("true") + 1);
        assertNotNull(help);
        assertEquals("demo.Dep", help.ownerQualified());
        assertEquals(List.of("int", "boolean"),
            help.parameters().stream().map(SignatureHelp.ParameterInfo::type).toList());
        assertNull(signatures.compute(store.capture(), use.id(), use.text().indexOf("true") + 1));
    }

    @Test
    public void capturedConfigurationPreservesClasspathPrecedenceAfterReconfiguration() throws Exception {
        Path first = library("first", "fromFirst");
        Path second = library("second", "fromSecond");
        DefaultDocumentStore store = store();
        store.publishConfiguration(configuration(1, first, second));
        WorkspaceSnapshot original = store.capture();
        store.publishConfiguration(configuration(2, second, first));
        var provider = new JavaWorkspaceAnalysisProvider();
        JavaWorkspaceAnalysis old = provider.capture(original);
        JavaWorkspaceAnalysis current = provider.capture(store.capture());
        assertEquals(List.of(first, second), old.classpath());
        assertEquals(List.of(second, first), current.classpath());
        assertEquals("fromFirst", old.lookupClassStub("demo.Library").fields().getFirst().name());
        assertEquals("fromSecond", current.lookupClassStub("demo.Library").fields().getFirst().name());
        assertSame(old, provider.capture(original));
    }

    private Path library(String folder, String field) throws Exception {
        Path root = directory.resolve(folder);
        Path file = root.resolve("demo/Library.class");
        Files.createDirectories(file.getParent());
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/Library", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, field, "I", null, null).visitEnd();
        writer.visitEnd();
        Files.write(file, writer.toByteArray());
        return root;
    }

    private WorkspaceConfigurationSnapshot configuration(long revision, Path first, Path second) {
        return new WorkspaceConfigurationSnapshot(revision, Set.of(":"),
            Map.of("module.:.classpath", first + "\n" + second));
    }

    private DefaultDocumentStore store() {
        return new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
    }

    private TextDocumentSnapshot source(String name, String text) {
        return new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(directory.resolve(name)),
            DocumentVersion.initial(), "java", text, StandardCharsets.UTF_8);
    }

    private TextDocumentSnapshot next(TextDocumentSnapshot previous, String text) {
        return new TextDocumentSnapshot(previous.id(), previous.uri(), previous.version().next(), "java", text,
            previous.encoding());
    }
}
