package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.sst.document.api.BackingKind;
import dev.railroadide.railroad.ide.sst.document.api.BinaryDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentOrigin;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.ProvenanceKind;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSignatureHelpProviderTest {
    private static final String SOURCE = """
        class Example {
            int combine(int count, boolean enabled) { return count; }
            void run() { combine(1, true); }
        }
        """;
    private static final int CARET = SOURCE.lastIndexOf("true") + 1;

    @TempDir
    public Path tempDirectory;

    @Test
    public void returnsTheResolvedOverloadFirstAndIncludesShorterAndZeroParameterAlternatives() {
        String source = """
            class Example {
                void call() {}
                void call(long count) {}
                void call(int row, String name) {}
                void run() { call(1, ""); }
            }
            """;
        TextDocumentSnapshot document = snapshot(DocumentUri.virtual("generated", "/Example.java"), source);
        var workspace = new WorkspaceSnapshot("signature-overloads", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        var context = new FeatureRequestContext(workspace, document, CancellationToken.none(), Deadline.none());
        int caret = source.indexOf("call(1, ") + "call(1, ".length();
        var provider = new JavaSignatureHelpProvider();
        List<SignatureHelp> signatures = provider.compute(context, caret, true).value();
        assertEquals(3, signatures.size());
        assertEquals(List.of("row", "name"),
            signatures.getFirst().parameters().stream().map(SignatureHelp.ParameterInfo::name).toList());
        assertEquals(1, signatures.getFirst().activeParameter());
        assertTrue(signatures.subList(1, 3).stream().allMatch(signature -> signature.activeParameter() == -1));
        assertTrue(signatures.stream().anyMatch(signature -> signature.parameters().isEmpty()));
        assertEquals(List.of(signatures.getFirst()), provider.compute(context, caret, false).value());
    }

    @Test
    public void providesSignatureHelpForGeneratedVirtualJavaDocument() {
        var snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("generated", "/demo/Example.java"),
            DocumentVersion.initial(),
            "java",
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED),
            SOURCE,
            StandardCharsets.UTF_8);
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();

        assertTrue(snapshot.uri().filePath().isEmpty());

        SignatureHelp help = assertDoesNotThrow(() -> provider.compute(snapshot, CARET));

        assertNotNull(help);
        assertEquals("Example", help.ownerQualified());
        assertEquals("combine", help.methodName());
        assertEquals(List.of("int", "boolean"),
            help.parameters().stream().map(SignatureHelp.ParameterInfo::type).toList());
        assertEquals(List.of("count", "enabled"),
            help.parameters().stream().map(SignatureHelp.ParameterInfo::name).toList());
        assertEquals(1, help.activeParameter());
        assertEquals("int", help.returnType());
        assertFalse(help.constructor());
    }

    @Test
    public void showsExpectedParametersInsideAnEmptyCallBeforeArgumentsAreTyped() {
        String text = SOURCE.replace("combine(1, true)", "combine()");
        TextDocumentSnapshot snapshot = snapshot(DocumentUri.virtual("generated", "/demo/Example.java"), text);
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();
        int inside = text.lastIndexOf("combine(") + "combine(".length();
        SignatureHelp help = provider.compute(snapshot, inside);
        assertNotNull(help);
        assertEquals(List.of("int", "boolean"),
            help.parameters().stream().map(SignatureHelp.ParameterInfo::type).toList());
        assertEquals(0, help.activeParameter());
        assertNull(provider.compute(snapshot, inside + 1));
    }

    @Test
    public void tracksTheArgumentAcrossTheCommaInAPartialCall() {
        String text = SOURCE.replace("combine(1, true)", "combine(1 /* , ) */ , )");
        TextDocumentSnapshot snapshot = snapshot(DocumentUri.virtual("generated", "/demo/Example.java"), text);
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();
        int comma = text.lastIndexOf(',');
        assertEquals(0, provider.compute(snapshot, comma).activeParameter());
        assertEquals(1, provider.compute(snapshot, comma + 1).activeParameter());
    }

    @Test
    public void usesTheInnermostCallAndIgnoresParenthesesInAChainedReceiver() {
        String text = "class Example { Example next() { return this; } int read(int i) { return i; }"
            + " int inner(int i) { return i; } void run() { next().read(inner(1)); } }";
        TextDocumentSnapshot snapshot = snapshot(DocumentUri.virtual("generated", "/demo/Example.java"), text);
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();
        int receiver = text.lastIndexOf("next(") + "next(".length();
        assertEquals("next", provider.compute(snapshot, receiver).methodName());
        assertNull(provider.compute(snapshot, receiver + 1));
        assertEquals("inner", provider.compute(snapshot, text.lastIndexOf("1)")).methodName());
        assertEquals("read", provider.compute(snapshot, text.lastIndexOf("1)") + 2).methodName());
    }

    @Test
    public void partialCallsChooseAnOverloadUsingAlreadyTypedArguments() {
        String text = "class Example { void write(boolean flag, long count) {} void write(int row, String value) {}"
            + " void run() { write(1, ); } }";
        var provider = new JavaSignatureHelpProvider();
        SignatureHelp help = provider.compute(snapshot(DocumentUri.virtual("generated", "/Example.java"), text),
            text.lastIndexOf(", ") + 2);
        assertNotNull(help);
        assertEquals(List.of("row", "value"),
            help.parameters().stream().map(SignatureHelp.ParameterInfo::name).toList());
        assertEquals(1, help.activeParameter());
    }

    @Test
    public void showsConstructorParametersAndKeepsTheVarargsParameterActive() {
        String text = "class Example { Example(int first, int... rest) {} void run() { new Example(1, 2, 3); } }";
        SignatureHelp help = new JavaSignatureHelpProvider().compute(
            snapshot(DocumentUri.virtual("generated", "/Example.java"), text),
            text.lastIndexOf("3)"));
        assertNotNull(help);
        assertTrue(help.constructor());
        assertTrue(help.varargs());
        assertEquals("int...", help.parameters().getLast().type());
        assertEquals("rest", help.parameters().getLast().name());
        assertEquals(1, help.activeParameter());
    }

    @Test
    public void providesSameSignatureForPhysicalArchiveAndUnnamedDocuments() {
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();
        SignatureHelp expected = provider
            .compute(snapshot(DocumentUri.virtual("generated", "/demo/Example.java"), SOURCE), CARET);
        assertNotNull(expected);

        for (DocumentUri uri : List.of(
            DocumentUri.fromPath(tempDirectory.resolve("Example.java")),
            DocumentUri.parse("jar:file:///libs/example.jar!/demo/Example.java"),
            DocumentUri.virtual("generated", "/"))) {
            assertEquals(expected, provider.compute(snapshot(uri, SOURCE), CARET));
        }
    }

    @Test
    public void usesUnsavedSnapshotTextInsteadOfPhysicalFileContents() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        Files.writeString(file, "class Example {}");
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();
        TextDocumentSnapshot snapshot = snapshot(DocumentUri.fromPath(file), SOURCE);

        SignatureHelp help = provider.compute(snapshot, CARET);

        assertNotNull(help);
        assertEquals("Example", help.ownerQualified());
        assertEquals("combine", help.methodName());
        assertEquals(1, help.activeParameter());
    }

    @Test
    public void rejectsBinaryAndNonJavaSnapshots() {
        DocumentUri uri = DocumentUri.virtual("generated", "/demo/Example.java");
        var binary = new BinaryDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            new byte[5]);
        var nonJava = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "plaintext", SOURCE,
            StandardCharsets.UTF_8);
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();

        assertNull(provider.compute(binary, CARET));
        assertNull(provider.compute(nonJava, CARET));
    }

    @Test
    public void returnsNoHelpForEmptyTextOrOutsideAnInvocation() {
        DocumentUri uri = DocumentUri.virtual("generated", "/demo/Example.java");
        SignatureHelpProvider provider = new JavaSignatureHelpProvider();

        assertNull(provider.compute(snapshot(uri, ""), 0));
        assertNull(provider.compute(snapshot(uri, SOURCE), -1));
        assertNull(provider.compute(snapshot(uri, SOURCE), 0));
    }

    @Test
    public void capturedClasspathIgnoresMissingDirectoriesAndStandaloneClassFiles() throws Exception {
        Path classes = Files.createDirectories(tempDirectory.resolve("classes"));
        Path library = classes.resolve("Library.class");
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Library", null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "answer", "(I)I", null, null)
            .visitEnd();
        writer.visitEnd();
        Files.write(library, writer.toByteArray());

        String source = "class Example { void run() { Library.answer(1); } }";
        SignatureHelpArchiveProbe.verify(tempDirectory, Path.of(System.getProperty("java.home")),
            List.of(tempDirectory.resolve("not-built"), library, classes), source, source.indexOf("1)") + 1,
            "Library", "answer", List.of("int"));
    }

    private static TextDocumentSnapshot snapshot(DocumentUri uri, String text) {
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java", text,
            StandardCharsets.UTF_8);
    }

    @Test
    public void takesParameterNamesFromTheExactAttachedOverload() throws Exception {
        Path binary = tempDirectory.resolve("library.jar");
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "lib/Library", null, "java/lang/Object", null);
        for (String parameter : List.of("I", "J", "Z")) {
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "read",
                "(" + parameter + ")I", null, null).visitEnd();
        }

        writer.visitEnd();
        try (var jar = new JarOutputStream(Files.newOutputStream(binary))) {
            jar.putNextEntry(new JarEntry("lib/Library.class"));
            jar.write(writer.toByteArray());
        }

        String source = "import lib.Library; class Example { int a = Library.read(1); int b = Library.read(true); }";
        TextDocumentSnapshot document = snapshot(DocumentUri.virtual("generated", "/Example.java"), source);
        var workspace = new WorkspaceSnapshot("signature", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of(":"), Map.of("module.:.classpath", binary.toString())), 0);
        try (var analysis = new JavaWorkspaceAnalysisProvider(() -> true,
            (roots, _) -> JavaLibrarySymbolIndex.build(roots))) {
            var provider = new JavaSignatureHelpProvider(context -> analysis.capture(context.workspaceSnapshot(),
                document.id()));
            int caret = source.indexOf("read(1") + "read(".length();
            assertEquals("arg0", provider.compute(workspace, document.id(), caret).parameters().getFirst().name());
            try (var jar = new JarOutputStream(Files.newOutputStream(tempDirectory.resolve("library-sources.jar")))) {
                jar.putNextEntry(new JarEntry("lib/Library.java"));
                jar.write("""
                    package lib;
                    public class Library {
                        public static native int read(long byteOffset);
                        public static native int read(int rowIndex);
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            }

            SignatureHelp.ParameterInfo parameter = provider.compute(workspace, document.id(), caret).parameters()
                .getFirst();
            assertEquals("rowIndex", parameter.name());
            assertEquals("int", parameter.type());
            var context = new FeatureRequestContext(workspace, document, CancellationToken.none(), Deadline.none());
            List<SignatureHelp> overloads = provider.compute(context, caret, true).value();
            assertEquals("rowIndex", overloads.getFirst().parameters().getFirst().name());
            assertEquals(Set.of("rowIndex", "byteOffset", "arg0"), overloads.stream()
                .map(overload -> overload.parameters().getFirst().name()).collect(Collectors.toSet()));
            int unmatched = source.indexOf("read(true") + "read(".length();
            assertEquals("arg0", provider.compute(workspace, document.id(), unmatched).parameters().getFirst().name());
        }
    }

    @Test
    public void capturedJava8ToolchainsResolveSignaturesFromTheirRuntimeArchive() throws Exception {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
        for (String layout : List.of("jre/lib/rt.jar", "lib/rt.jar")) {
            Path jdk = Files.createTempDirectory(tempDirectory, "jdk8-");
            Path archive = jdk.resolve(layout);
            Files.createDirectories(archive.getParent());
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
                var object = new ClassWriter(0);
                object.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "java/lang/Object", null, null, null);
                object.visitEnd();
                jar.putNextEntry(new JarEntry("java/lang/Object.class"));
                jar.write(object.toByteArray());
                jar.closeEntry();
                var string = new ClassWriter(0);
                string.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "java/lang/String", null,
                    "java/lang/Object", null);
                string.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "substring", "(I)Ljava/lang/String;",
                    null, null).visitEnd();
                string.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "legacyOnly", "()I", null, null).visitEnd();
                string.visitEnd();
                jar.putNextEntry(new JarEntry("java/lang/String.class"));
                jar.write(string.toByteArray());
                jar.closeEntry();
            }

            String source = "class Example { void run(String text) { text.substring(1); text.legacyOnly(); } }";
            SignatureHelpArchiveProbe.verify(tempDirectory, jdk, List.of(), source, source.indexOf("1)") + 1,
                "java.lang.String", "substring", List.of("int"));
            SignatureHelpArchiveProbe.verify(tempDirectory, jdk, List.of(), source, source.indexOf("legacyOnly(") + 11,
                "java.lang.String", "legacyOnly", List.of());
            JavaJdkSymbolIndex index = JavaJdkSymbolIndex.build(jdk);
            assertFalse(index.lookupMember("java.lang.String", "legacyOnly").isEmpty());
            assertFalse(index.declaredQualifiedNames().contains("java.lang.Record"));
        }
    }
}
