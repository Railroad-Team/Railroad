package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.sst.document.api.BinaryDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompletionProviderTest {
    private static final String SOURCE = "/* 😀 */ class Example { int marker; void run() { this.marker = 1; } }";

    @TempDir
    public Path projectRoot;

    @BeforeAll
    public static void initializeWorkspaceModes() {
        WorkspaceModes.initialize();
    }

    @Test
    public void completesMembersFromPhysicalAndVirtualSnapshots() {
        for (CompletionProvider provider : providers()) {
            for (DocumentUri uri : List.of(
                DocumentUri.fromPath(projectRoot.resolve("Example.java")),
                DocumentUri.virtual("generated", "/demo/Example.java"),
                DocumentUri.parse("jar:file:///libs/example.jar!/demo/Example.java"),
                DocumentUri.inMemory(DocumentId.create()),
                DocumentUri.virtual("generated", "/"))) {
                var snapshot = snapshot(uri, SOURCE);
                int triggerAt = SOURCE.indexOf("this.") + "this".length();

                CompletionResult result = assertDoesNotThrow(() -> provider.compute(snapshot, triggerAt));

                assertNotNull(result,
                    () -> provider.getClass().getSimpleName() + " returned no completions for " + uri);
                assertEquals(triggerAt, result.dotIndex());
                assertTrue(hasItem(result, "marker"));
            }
        }
    }

    @Test
    public void usesUnsavedSnapshotTextInsteadOfPhysicalFileContents() throws Exception {
        Path file = projectRoot.resolve("Example.java");
        Files.writeString(file, "class Example { int diskOnly; }");
        var snapshot = snapshot(DocumentUri.fromPath(file), SOURCE);
        int triggerAt = SOURCE.indexOf("this.") + "this".length();

        for (CompletionProvider provider : providers()) {
            CompletionResult result = provider.compute(snapshot, triggerAt);

            assertNotNull(result);
            assertTrue(hasItem(result, "marker"));
            assertFalse(hasItem(result, "diskOnly"));
        }
    }

    @Test
    public void retainedSnapshotKeepsItsCompletionsAfterContentChanges() {
        var mutableSource = new StringBuilder(SOURCE);
        var original = snapshot(DocumentUri.virtual("generated", "/demo/Example.java"), mutableSource);
        String updatedSource = SOURCE.replace("marker", "updated");
        mutableSource.replace(0, mutableSource.length(), updatedSource);
        var updated = new TextDocumentSnapshot(original.id(), original.uri(), original.version().next(),
            original.languageId(), mutableSource, original.encoding());
        int originalTrigger = SOURCE.indexOf("this.") + "this".length();
        int updatedTrigger = updatedSource.indexOf("this.") + "this".length();

        for (CompletionProvider provider : providers()) {
            CompletionResult originalResult = provider.compute(original, originalTrigger);
            CompletionResult updatedResult = provider.compute(updated, updatedTrigger);

            assertNotNull(originalResult);
            assertNotNull(updatedResult);
            assertTrue(hasItem(originalResult, "marker"));
            assertFalse(hasItem(originalResult, "updated"));
            assertTrue(hasItem(updatedResult, "updated"));
            assertFalse(hasItem(updatedResult, "marker"));
            assertEquals(originalResult, provider.compute(original, originalTrigger));
        }
    }

    @Test
    public void rejectsBinaryAndNonJavaSnapshots() {
        DocumentUri uri = DocumentUri.virtual("generated", "/demo/Example.java");
        var binary = new BinaryDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(),
            "java", SOURCE.getBytes(StandardCharsets.UTF_8));
        var nonJava = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(),
            "plaintext", SOURCE, StandardCharsets.UTF_8);
        int triggerAt = SOURCE.indexOf("this.") + "this".length();

        for (CompletionProvider provider : providers()) {
            assertNull(provider.compute(binary, triggerAt));
            assertNull(provider.compute(nonJava, triggerAt));
        }
    }

    @Test
    public void rejectsEmptyTextAndInvalidTriggers() {
        DocumentUri uri = DocumentUri.virtual("generated", "/demo/Example.java");
        var snapshot = snapshot(uri, SOURCE);

        for (CompletionProvider provider : providers()) {
            assertNull(provider.compute(snapshot(uri, ""), 0));
            assertNull(provider.compute(snapshot, -1));
            assertNull(provider.compute(snapshot, SOURCE.length()));
            assertNull(provider.compute(snapshot, SOURCE.indexOf("marker")));
        }
    }

    private List<CompletionProvider> providers() {
        Project project = (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPath" -> projectRoot;
                case "getAlias" -> "Completion test project";
                case "hasFacet" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "CompletionTestProject[" + projectRoot + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return List.of(new JavaCompletionProvider(project), new JdtCompletionProvider(new String[0]));
    }

    private static TextDocumentSnapshot snapshot(DocumentUri uri, CharSequence source) {
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java", source,
            StandardCharsets.UTF_8);
    }

    private static boolean hasItem(CompletionResult result, String name) {
        return result.items().stream().anyMatch(item -> name.equals(item.insertText()));
    }
}
