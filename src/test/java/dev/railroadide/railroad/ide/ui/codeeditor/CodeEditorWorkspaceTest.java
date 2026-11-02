package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.completion.*;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.signature.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorWorkspaceTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void startFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void diagnosticsRetainCapturedWorkspaceAndRejectResultsAfterEdit() throws Exception {
        assertCapturedRequest("diagnostics");
    }

    @Test
    public void completionRetainsCapturedWorkspaceAndRejectsResultsAfterEdit() throws Exception {
        assertCapturedRequest("completion");
    }

    @Test
    public void completionTriggeredByEditUsesUpdatedWorkspace() throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, "class Example { String text; }");
        DocumentId id = DocumentId.create();
        var store = new DefaultDocumentStore("test",
            new WorkspaceConfigurationSnapshot(0, Set.of()));
        CompletableFuture<DocumentSnapshot> requested = new CompletableFuture<>();
        CompletionProvider completion = (snapshot, offset) -> {
            requested.complete(snapshot);
            return null;
        };
        CodeEditorPane editor = onFx(() -> {
            Project project = new RailroadProject(directory, "Live completion", new WritableImage(1, 1));
            var config = new CodeEditorConfig("java", completion, null, null, null);
            CodeEditorPane pane = new CodeEditorPane(project, id, file, config) {
                @Override
                protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
                    return false;
                }

                @Override
                protected boolean supportsHighlighting() {
                    return false;
                }
            };
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            pane.appendText(".");
            return pane;
        });
        try {
            TextDocumentSnapshot snapshot = (TextDocumentSnapshot) requested.get(10, TimeUnit.SECONDS);
            assertEquals("class Example { String text; }.", snapshot.text());
            assertSame(store.current(id).orElseThrow(), snapshot);
        } finally {
            onFx(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
        }
    }

    @Test
    public void signatureHelpRetainsCapturedWorkspaceAndRejectsResultsAfterEdit() throws Exception {
        assertCapturedRequest("signature");
    }

    @Test
    public void existingProvidersReceiveExactDocumentFromCapturedWorkspace() {
        DocumentId id = DocumentId.create();
        var document = new TextDocumentSnapshot(id, DocumentUri.inMemory(id),
            new DocumentVersion(4), "java", "text", StandardCharsets.UTF_8);
        var store = new DefaultDocumentStore("legacy",
            new WorkspaceConfigurationSnapshot(0, Set.of()));
        store.publishBacking(document);
        WorkspaceSnapshot workspace = store.capture();
        AtomicReference<DocumentSnapshot> received = new AtomicReference<>();
        CompletionProvider completion = (snapshot, offset) -> {
            received.set(snapshot);
            return null;
        };
        completion.compute(workspace, id, 0);
        assertSame(document, received.get());
        SignatureHelpProvider signature = (snapshot, offset) -> {
            received.set(snapshot);
            return null;
        };
        signature.compute(workspace, id, 0);
        assertSame(document, received.get());
        DiagnosticsProvider<TextEditorDiagnostic> diagnostics = snapshot -> {
            received.set(snapshot);
            return List.of();
        };
        diagnostics.compute(workspace, id);
        assertSame(document, received.get());
    }

    private void assertCapturedRequest(String feature) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, "class Example {}");
        var release = new CountDownLatch(1);
        CompletableFuture<WorkspaceSnapshot> entered = new CompletableFuture<>();
        AtomicReference<WorkspaceSnapshot> completedWith = new AtomicReference<>();
        CompletionProvider completion = new CompletionProvider() {
            @Override
            public CompletionResult compute(DocumentSnapshot snapshot, int offset) {
                throw new AssertionError("Editor must pass the captured workspace");
            }

            @Override
            public CompletionResult compute(WorkspaceSnapshot workspace, DocumentId id, int offset) {
                block(workspace, entered, release);
                completedWith.set(workspace);
                return new CompletionResult(offset, List.of(new CompletionItem("old", "old")));
            }
        };
        DiagnosticsProvider<TextEditorDiagnostic> diagnostics = new DiagnosticsProvider<>() {
            @Override
            public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
                throw new AssertionError("Editor must pass the captured workspace");
            }

            @Override
            public List<TextEditorDiagnostic> compute(WorkspaceSnapshot workspace, DocumentId id) {
                block(workspace, entered, release);
                completedWith.set(workspace);
                TextDocumentSnapshot snapshot = (TextDocumentSnapshot) workspace.document(id).orElseThrow();
                return List.of(new TextEditorDiagnostic(Location.TextLocation.from(snapshot, 0, 1),
                    Diagnostic.Kind.ERROR, "OLD", "old result"));
            }
        };
        SignatureHelpProvider signature = new SignatureHelpProvider() {
            @Override
            public SignatureHelp compute(DocumentSnapshot snapshot, int caret) {
                throw new AssertionError("Editor must pass the captured workspace");
            }

            @Override
            public SignatureHelp compute(WorkspaceSnapshot workspace, DocumentId id, int caret) {
                block(workspace, entered, release);
                completedWith.set(workspace);
                return new SignatureHelp("Example", "Example", "old", List.of(), 0, false, "void", false);
            }
        };
        DocumentId id = DocumentId.create();
        DocumentId otherId = DocumentId.create();
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var other = new TextDocumentSnapshot(otherId, DocumentUri.inMemory(otherId),
            DocumentVersion.initial(), "java", "other original", StandardCharsets.UTF_8);
        store.publishBacking(other);
        TestEditor editor = onFx(() -> {
            Project project = new RailroadProject(directory, "Workspace request", new WritableImage(1, 1));
            var config = new CodeEditorConfig("java", feature.equals("completion") ? completion : null,
                feature.equals("diagnostics") ? diagnostics : null, feature.equals("signature") ? signature : null,
                null);
            var pane = new TestEditor(project, id, file, config);
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            if (feature.equals("completion")) {
                Method trigger = CodeEditorPane.class.getDeclaredMethod("triggerCompletion",
                    int.class);
                trigger.setAccessible(true);
                trigger.invoke(pane, 0);
            } else if (feature.equals("signature")) {
                Method trigger = CodeEditorPane.class.getDeclaredMethod("requestSignatureHelp",
                    boolean.class);
                trigger.setAccessible(true);
                trigger.invoke(pane, true);
            }
            return pane;
        });
        try {
            WorkspaceSnapshot captured = entered.get(10, TimeUnit.SECONDS);
            TextDocumentSnapshot original = (TextDocumentSnapshot) captured.document(id).orElseThrow();
            onFx(() -> {
                editor.replaceText("class Example { int later; }");
                store.publishBacking(new TextDocumentSnapshot(otherId, other.uri(), other.version().next(),
                    "java", "other updated", StandardCharsets.UTF_8));
                return null;
            });
            assertEquals("class Example {}", original.text());
            assertSame(other, captured.document(otherId).orElseThrow());
            release.countDown();
            editor.awaitWorkerCompletion();
            onFx(() -> {
                assertSame(captured, completedWith.get());
                assertEquals("class Example {}",
                    ((TextDocumentSnapshot) completedWith.get().document(id).orElseThrow()).text());
                assertTrue(editor.visibleDiagnostics.isEmpty());
                assertTrue(editor.completionCandidates.isEmpty());
                assertNull(editor.activeSignatureHelp.get());
                return null;
            });
        } finally {
            release.countDown();
            onFx(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
        }
    }

    private static void block(
        WorkspaceSnapshot workspace,
        CompletableFuture<WorkspaceSnapshot> entered,
        CountDownLatch release
    ) {
        entered.complete(workspace);
        try {
            assertTrue(release.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static final class TestEditor extends CodeEditorPane {
        private boolean enabled;

        private TestEditor(Project project, DocumentId id, Path file, CodeEditorConfig config) {
            super(project, id, file, config);
            enabled = true;
        }

        @Override
        protected boolean supportsDiagnostics() {
            return enabled && super.supportsDiagnostics();
        }

        @Override
        protected boolean supportsCompletion() {
            return enabled && super.supportsCompletion();
        }

        @Override
        protected boolean supportsSignatureHelp() {
            return enabled && super.supportsSignatureHelp();
        }

        @Override
        protected boolean supportsHighlighting() {
            return false;
        }

        @Override
        protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
            return true;
        }

        private void awaitWorkerCompletion() throws Exception {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) worker;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ((executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(0, executor.getActiveCount());
            assertTrue(executor.getQueue().isEmpty());
        }
    }
}
