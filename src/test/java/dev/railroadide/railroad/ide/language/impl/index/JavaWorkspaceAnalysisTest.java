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
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticCompletionEngine;
import dev.railroadide.railroad.ide.sst.project.JavaSourceSnapshotIndex;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

public class JavaWorkspaceAnalysisTest {
    @TempDir
    public Path directory;

    @Test
    public void capturedSourceShadowsAnOlderBinaryOfTheSameType() throws Exception {
        DefaultDocumentStore store = store();
        Path binary = library("old-classes", "binaryOnly");
        store.publishConfiguration(configuration(1, binary));
        TextDocumentSnapshot dependency = source("Library.java",
            "package demo; public class Library { public int sourceOnly; }");
        TextDocumentSnapshot use = source("Use.java", "package demo; class Use { void run(Library l) { l. } }");
        store.publishBacking(dependency);
        store.publishBacking(use);
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (_, method, _) -> {
                throw new AssertionError("Live project access: " + method.getName());
            });
        CompletionResult result = new JavaCompletionProvider(project).compute(store.capture(), use.id(),
            use.text().indexOf("l.") + 1);
        assertNotNull(result);
        assertTrue(result.items().stream().anyMatch(item -> item.insertText().equals("sourceOnly")));
        assertFalse(result.items().stream().anyMatch(item -> item.insertText().equals("binaryOnly")));
    }

    @Test
    public void signatureHelpUsesCapturedSourceInsteadOfItsOlderClassFile() throws Exception {
        DefaultDocumentStore store = store();
        Path binary = library("old-signature-classes", "binaryOnly");
        store.publishConfiguration(configuration(1, binary));
        TextDocumentSnapshot dependency = source("Library.java",
            "package demo; public class Library { public int captured(int count) { return count; } }");
        TextDocumentSnapshot use = source("Use.java",
            "package demo; class Use { void run(Library l) { l.captured(1); } }");
        store.publishBacking(dependency);
        store.publishBacking(use);
        SignatureHelp help = new JdtJavaSignatureHelpProvider(new String[0]).compute(store.capture(), use.id(),
            use.text().indexOf("1)") + 1);
        assertNotNull(help);
        assertEquals("captured", help.methodName());
        assertEquals("int", help.parameters().getFirst().type());
    }

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

    @Test
    public void capturedEnclosingTypeHidesRemovedBinaryDescendantsAcrossAllLookups() throws Exception {
        Path binaries = directory.resolve("nested-classes");
        writeClass(binaries, "demo/Outer", null, null, "java/lang/Object", null);
        writeClass(binaries, "demo/Outer$Removed", "demo/Outer", null, "java/lang/Object", null);
        writeClass(binaries, "demo/Outer$Removed$Deep", "demo/Outer$Removed", null, "java/lang/Object", null);
        writeClass(binaries, "demo/Outer$Dollar", null, null, "java/lang/Object", null);
        DefaultDocumentStore store = store();
        store.publishConfiguration(configuration(1, binaries));
        store.publishBacking(source("Outer.java", "package demo; public class Outer {}"));
        JavaWorkspaceAnalysis analysis = new JavaWorkspaceAnalysisProvider().capture(store.capture());
        for (String name : List.of("demo.Outer$Removed", "demo.Outer$Removed$Deep")) {
            assertFalse(analysis.declaredQualifiedNames().contains(name));
            assertFalse(analysis.typeNames().contains(name));
            assertNull(analysis.lookupClassStub(name));
            assertTrue(analysis.lookupQualifiedName(name).isEmpty());
            assertTrue(analysis.lookupQualifiedName(name + "#binaryOnly").isEmpty());
            assertTrue(analysis.lookupSimpleName(name.substring("demo.".length())).isEmpty());
            assertTrue(analysis.lookupMembers(name).isEmpty());
            assertTrue(analysis.lookupMember(name, "binaryOnly").isEmpty());
        }
        assertFalse(analysis.typeNames().contains("Removed"));
        assertFalse(analysis.typeNames().contains("Deep"));
        assertEquals(List.of("demo.Outer$Dollar"), analysis.lookupSimpleName("binaryOnly").stream()
            .map(symbol -> symbol.ownerQualifiedName()).toList());
        assertNotNull(analysis.lookupClassStub("demo.Outer$Dollar"));
        assertFalse(analysis.lookupMember("demo.Outer$Dollar", "binaryOnly").isEmpty());
    }

    @Test
    public void binaryNestedAliasesPreserveDollarCharactersInPackageAndTypeIdentifiers() {
        DefaultDocumentStore store = store();
        store.publishBacking(source("Money$Box.java",
            "package demo$; public class Money$Box { public static class Node$Leaf { public int value; } }"));
        JavaWorkspaceAnalysis analysis = new JavaWorkspaceAnalysisProvider().capture(store.capture());
        String sourceName = "demo$.Money$Box.Node$Leaf";
        String binaryName = "demo$.Money$Box$Node$Leaf";
        assertEquals(sourceName, analysis.canonicalTypeName(binaryName));
        assertEquals(analysis.lookupMembers(sourceName), analysis.lookupMembers(binaryName));
        assertEquals(analysis.lookupMember(sourceName, "value"), analysis.lookupMember(binaryName, "value"));
        assertFalse(analysis.lookupMember(binaryName, "value").isEmpty());
        assertEquals(analysis.lookupQualifiedName(sourceName + "#value"),
            analysis.lookupQualifiedName(binaryName + "#value"));
        assertTrue(analysis.lookupQualifiedName("demo.Money.Box.Node.Leaf").isEmpty());
    }

    @Test
    public void binaryReturnAndSuperclassTypesResolveCapturedNestedMembersAndInheritance() throws Exception {
        Path binaries = directory.resolve("binary-callers");
        writeClass(binaries, "demo/Outer", null, null, "java/lang/Object", null);
        writeClass(binaries, "demo/Outer$Inner", "demo/Outer", null, "java/lang/Object", null);
        writeClass(binaries, "demo/Derived", null, "Ldemo/Outer$Inner<Ljava/lang/String;>;",
            "demo/Outer$Inner", null);
        writeClass(binaries, "demo/Factory", null, null, "java/lang/Object",
            writer -> writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "value",
                "()Ldemo/Outer$Inner;", "()Ldemo/Outer$Inner<Ljava/lang/String;>;", null).visitEnd());
        DefaultDocumentStore store = store();
        store.publishConfiguration(configuration(1, binaries));
        store.publishBacking(source("Outer.java", """
            package demo;
            public class Outer {
                public static class Inner<T> extends Base<T> {
                    public int sourceOnly;
                    public T convert(T value) { return value; }
                }
            }
            class Base<T> {
                public int inheritedField;
                public T inherited(T value) { return value; }
            }
            """));
        TextDocumentSnapshot use = source("Use.java", """
            package demo;
            class Use {
                void run(Derived derived) {
                    String a = Factory.value().convert("a");
                    String b = Factory.value().inherited("b");
                    String c = derived.convert("c");
                    String d = derived.inherited("d");
                    int sum = Factory.value().sourceOnly + derived.inheritedField;
                }
            }
            """);
        store.publishBacking(use);
        var diagnostics = new JavaDiagnosticsProvider().compute(store.capture(), use.id());
        assertTrue(diagnostics.stream().noneMatch(diagnostic -> diagnostic.kind() == DiagnosticSeverity.ERROR),
            diagnostics::toString);
        JavaWorkspaceAnalysis analysis = new JavaWorkspaceAnalysisProvider().capture(store.capture());
        for (String receiver : List.of("Factory.value()", "derived")) {
            String text = "package demo; class Complete { void run(Derived derived) { " + receiver + ". } }";
            CompletionResult completion = JavaSemanticCompletionEngine.compute(text,
                text.lastIndexOf(". }"), analysis);
            assertNotNull(completion);
            Set<String> names = new HashSet<>(completion.items().stream().map(item -> item.insertText()).toList());
            assertTrue(names.containsAll(Set.of("sourceOnly", "inheritedField")), names::toString);
            assertFalse(names.contains("binaryOnly"));
        }
    }

    private void writeClass(
        Path root,
        String name,
        String enclosing,
        String signature,
        String parent,
        Consumer<ClassWriter> members
    ) throws Exception {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, signature, parent, null);
        if (enclosing != null) {
            writer.visitInnerClass(name, enclosing, name.substring(enclosing.length() + 1),
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        }
        if (name.startsWith("demo/Outer")) {
            writer.visitField(Opcodes.ACC_PUBLIC, "binaryOnly", "I", null, null).visitEnd();
        }
        if (members != null) {
            members.accept(writer);
        }
        writer.visitEnd();
        Path file = root.resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }

    @Test
    public void diagnosticsAndChainedCompletionUseCapturedSignaturesDespiteConflictingDiskText() throws Exception {
        assertCapturedDependencyBodies(true);
    }

    @Test
    public void diagnosticsAndChainedCompletionUseDependenciesThatOnlyExistInTheCapture() throws Exception {
        assertCapturedDependencyBodies(false);
    }

    private void assertCapturedDependencyBodies(boolean writeDisk) throws Exception {
        DefaultDocumentStore store = store();
        TextDocumentSnapshot dependency = source("Dep.java",
            "package demo; public class Dep { public Result convert(int count) { return null; } } "
                + "class Result { public int capturedField; public void capturedMethod() {} }");
        TextDocumentSnapshot use = source("Use.java",
            "package demo; class Use { void run(Dep d) { d.convert(1).capturedMethod(); } }");
        store.publishBacking(dependency);
        store.publishBacking(use);
        if (writeDisk) {
            Files.writeString(dependency.uri().filePath().orElseThrow(),
                "package demo; public class Dep { public DiskResult convert(String count) { return null; } } "
                    + "class DiskResult { public int diskField; }");
        }
        WorkspaceSnapshot captured = store.capture();
        store.publishOverlay(next(dependency, "package demo; public class Dep {}"));
        var diagnostics = new JavaDiagnosticsProvider().compute(captured, use.id());
        assertTrue(diagnostics.stream().noneMatch(diagnostic -> diagnostic.kind() == DiagnosticSeverity.ERROR),
            diagnostics::toString);

        TextDocumentSnapshot completion = source("Complete.java",
            "package demo; class Complete { void run(Dep d) { d.convert(1). } }");
        Map<DocumentId, DocumentSnapshot> documents = new HashMap<>(captured.documents());
        documents.put(completion.id(), completion);
        var request = new WorkspaceSnapshot(captured.projectId(), documents,
            captured.configuration(), captured.revision());
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
            new Class<?>[]{Project.class}, (_, method, _) -> {
                throw new AssertionError("Live project access: " + method.getName());
            });
        CompletionResult result = new JavaCompletionProvider(project).compute(request, completion.id(),
            completion.text().indexOf(").") + 1);
        assertNotNull(result);
        assertTrue(result.items().stream().anyMatch(item -> item.insertText().equals("capturedField")),
            () -> result.items().toString());
        assertFalse(result.items().stream().anyMatch(item -> item.insertText().equals("diskField")));
    }

    @Test
    public void sourceRootsExcludeConflictingFixturesAndKeepGeneratedSourcesAndRequestingBuffers() throws Exception {
        DefaultDocumentStore store = store();
        Path sources = directory.resolve("src/main/java");
        Path generated = directory.resolve("generated");
        store.publishConfiguration(new WorkspaceConfigurationSnapshot(1, Set.of(":"),
            Map.of("module.:.sources", sources + ":generated=false\n" + generated + ":test:generated=true")));
        TextDocumentSnapshot dependency = source("src/main/java/Dep.java",
            "package demo; public class Dep { public int call(int count) { return count; } }");
        TextDocumentSnapshot fixture = source("fixtures/Dep.java",
            "package demo; public class Dep { public int call(boolean enabled) { return 0; } }");
        TextDocumentSnapshot generatedType = source("generated/Generated.java",
            "package demo; public class Generated { public int inherited; }");
        TextDocumentSnapshot use = source("scratch/Use.java",
            "package demo; class Use extends Generated { void run(Use u, Dep d) { d.call(1); u. } }");
        for (TextDocumentSnapshot document : List.of(dependency, fixture, generatedType, use)) {
            store.publishBacking(document);
        }
        WorkspaceSnapshot workspace = store.capture();
        var provider = new JavaWorkspaceAnalysisProvider();
        JavaWorkspaceAnalysis analysis = provider.capture(workspace, use.id());
        assertEquals(Set.of(dependency.uri().filePath().orElseThrow(), generatedType.uri().filePath().orElseThrow(),
            use.uri().filePath().orElseThrow()), analysis.sourceTexts().keySet());
        assertFalse(provider.capture(workspace).sourceTexts().containsKey(use.uri().filePath().orElseThrow()));
        assertSame(analysis, provider.capture(workspace, use.id()));
        SignatureHelp signature = new JdtJavaSignatureHelpProvider(new String[0]).compute(workspace, use.id(),
            use.text().indexOf("1)") + 1);
        assertNotNull(signature);
        assertEquals("int", signature.parameters().getFirst().type());
        CompletionResult completion = JavaSemanticCompletionEngine.compute(
            use.text(), use.text().indexOf("u.") + 1, analysis);
        assertNotNull(completion);
        assertTrue(completion.items().stream().anyMatch(item -> item.insertText().equals("inherited")));

        var virtual = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.virtual("generated", "/demo/Virtual.java"), DocumentVersion.initial(), "java",
            "package demo; class Virtual { void run(Dep d) { d.call(1); } }", StandardCharsets.UTF_8);
        store.publishBacking(virtual);
        assertNotNull(new JdtJavaSignatureHelpProvider(new String[0]).compute(store.capture(), virtual.id(),
            virtual.text().indexOf("1)") + 1));
    }

    @Test
    public void missingCapturedSourceDoesNotFallBackToDisk() throws Exception {
        TextDocumentSnapshot dependency = source("Dep.java", "package demo; class Dep {}");
        DefaultDocumentStore store = store();
        store.publishBacking(dependency);
        JavaWorkspaceAnalysis captured = new JavaWorkspaceAnalysisProvider().capture(store.capture());
        var missing = new JavaWorkspaceAnalysis(captured.sourceIndex(), captured.symbols(),
            Map.of(), captured.classpath(), captured.jdkHome());
        Path path = dependency.uri().filePath().orElseThrow();
        Files.writeString(path, "package demo; class Dep { int diskOnly; }");
        assertThrows(NoSuchFileException.class,
            () -> JavaSourceSnapshotIndex.readSource(missing, path));
        assertEquals(Files.readString(path),
            JavaSourceSnapshotIndex.readSource(captured.sourceIndex(), path));
    }

    private WorkspaceConfigurationSnapshot configuration(long revision, Path... classpath) {
        return new WorkspaceConfigurationSnapshot(revision, Set.of(":"),
            Map.of("module.:.classpath", String.join("\n", Arrays.stream(classpath).map(Path::toString).toList()),
                "module.:.sources", directory + ":generated=false"));
    }

    private DefaultDocumentStore store() {
        return new DefaultDocumentStore("test", configuration(0));
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
