package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.sst.document.api.BackingKind;
import dev.railroadide.railroad.ide.sst.document.api.BinaryDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentOrigin;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.ProvenanceKind;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JdtJavaSignatureHelpProviderTest {
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
    public void providesSignatureHelpForGeneratedVirtualJavaDocument() {
        var snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("generated", "/demo/Example.java"),
            DocumentVersion.initial(),
            "java",
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED),
            SOURCE,
            StandardCharsets.UTF_8);
        SignatureHelpProvider provider = new JdtJavaSignatureHelpProvider(new String[0]);

        assertTrue(snapshot.uri().filePath().isEmpty());

        SignatureHelp help = assertDoesNotThrow(() -> provider.compute(snapshot, CARET));

        assertNotNull(help);
        assertEquals("Example", help.ownerQualified());
        assertEquals("combine", help.methodName());
        assertEquals(List.of("int", "boolean"),
            help.parameters().stream().map(SignatureHelp.ParameterInfo::type).toList());
        assertEquals(1, help.activeParameter());
        assertEquals("int", help.returnType());
        assertFalse(help.constructor());
    }

    @Test
    public void providesSameSignatureForPhysicalArchiveAndUnnamedDocuments() {
        SignatureHelpProvider provider = new JdtJavaSignatureHelpProvider(new String[0]);
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
    public void usesUnsavedSnapshotTextInsteadOfStoredFileContext() throws Exception {
        Path file = tempDirectory.resolve("Other.java");
        Files.writeString(file, "class Other {}");
        SignatureHelpProvider provider = new JdtJavaSignatureHelpProvider(file, new String[0]);
        var snapshot = snapshot(DocumentUri.fromPath(tempDirectory.resolve("Example.java")), SOURCE);

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
        SignatureHelpProvider provider = new JdtJavaSignatureHelpProvider(new String[0]);

        assertNull(provider.compute(binary, CARET));
        assertNull(provider.compute(nonJava, CARET));
    }

    @Test
    public void returnsNoHelpForEmptyTextOrOutsideAnInvocation() {
        DocumentUri uri = DocumentUri.virtual("generated", "/demo/Example.java");
        SignatureHelpProvider provider = new JdtJavaSignatureHelpProvider(new String[0]);

        assertNull(provider.compute(snapshot(uri, ""), 0));
        assertNull(provider.compute(snapshot(uri, SOURCE), -1));
        assertNull(provider.compute(snapshot(uri, SOURCE), 0));
    }

    private static TextDocumentSnapshot snapshot(DocumentUri uri, String text) {
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java", text,
            StandardCharsets.UTF_8);
    }
}
