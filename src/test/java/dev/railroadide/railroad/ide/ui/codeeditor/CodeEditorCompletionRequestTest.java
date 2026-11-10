package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.completion.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorCompletionRequestTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void initializeFx() throws Exception {
        startFx();
        onFx(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @Test
    public void acceptedCompletionKeepsItsRequestActiveWhileBuildingThePopup() throws Exception {
        try (Fixture fixture = open(context -> completion(context, "ACCEPTED"))) {
            FeatureRequestContext context = fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertEquals(List.of("ACCEPTED"), fixture.editor.candidates());
                assertFalse(context.cancellationToken().isCancellationRequested());
                assertTrue(fixture.editor.activeCompletionPopup.get().isShowing());
                return null;
            });
        }
    }

    @Test
    public void supersededRequestCannotOverwriteNewerCandidates() throws Exception {
        var releaseOld = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                await(releaseOld);
            }
            return completion(context, call == 1 ? "OLD" : "NEW");
        })) {
            try {
                FeatureRequestContext old = fixture.request();
                FeatureRequestContext current = fixture.request();
                assertTrue(old.cancellationToken().isCancellationRequested());
                assertNotSame(old, current);
                awaitWorkers(fixture.editor.worker, 1);
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.candidates());
                    return null;
                });

                releaseOld.countDown();
                awaitWorkers(fixture.editor.worker, 0);
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.candidates());
                    return null;
                });
            } finally {
                releaseOld.countDown();
            }
        }
    }

    @Test
    public void escapeWhileComputingPreventsLateCompletionFromReopeningThePopup() throws Exception {
        assertEscapeRejectsCompletion(false);
    }

    @Test
    public void escapeRejectsCompletionAlreadyWaitingInTheFxQueue() throws Exception {
        assertEscapeRejectsCompletion(true);
    }

    private void assertEscapeRejectsCompletion(boolean alreadyQueued) throws Exception {
        var releaseResult = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(releaseResult);
            return completion(context, "DISMISSED");
        })) {
            try {
                FeatureRequestContext context = fixture.request();
                if (alreadyQueued) {
                    try (FxBlocker blocker = FxBlocker.start(fixture.editor::escape)) {
                        releaseResult.countDown();
                        awaitWorkers(fixture.editor.worker, 0);
                        blocker.releaseAndAwait();
                    }
                } else {
                    onFx(() -> {
                        fixture.editor.escape();
                        return null;
                    });
                    releaseResult.countDown();
                    awaitWorkers(fixture.editor.worker, 0);
                }
                assertTrue(context.cancellationToken().isCancellationRequested());
                onFx(() -> {
                    assertTrue(fixture.editor.candidates().isEmpty(),
                        "Dismissed request must not repopulate candidates");
                    assertNull(fixture.editor.activeCompletionPopup.get());
                    return null;
                });
            } finally {
                releaseResult.countDown();
            }
        }
    }

    @Test
    public void aDifferentButEqualContextCannotPublishCompletion() throws Exception {
        try (Fixture fixture = open(context -> {
            var different = new FeatureRequestContext(context.workspaceSnapshot(), context.documentSnapshot(),
                context.cancellationToken(), context.deadline());
            assertNotSame(context, different);
            assertEquals(context, different);
            return completion(different, "WRONG_REQUEST");
        })) {
            fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertTrue(fixture.editor.candidates().isEmpty());
                return null;
            });
        }
    }

    @Test
    public void unsupportedNullPayloadDismissesCompletionWithoutAnFxFailure() throws Exception {
        try (Fixture fixture = open(context -> new FeatureResult<>(context, null))) {
            FeatureRequestContext context = fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertTrue(fixture.editor.candidates().isEmpty());
                assertNull(fixture.editor.activeCompletionPopup.get());
                assertTrue(context.cancellationToken().isCancellationRequested(),
                    "Unsupported completion dismisses the request");
                fixture.assertNoFxFailures();
                return null;
            });
        }
    }

    @Test
    public void emptyPayloadDismissesCompletionWithoutAnFxFailure() throws Exception {
        try (Fixture fixture = open(context -> new FeatureResult<>(context, CompletionResult.empty()))) {
            FeatureRequestContext context = fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertTrue(fixture.editor.candidates().isEmpty());
                assertNull(fixture.editor.activeCompletionPopup.get());
                assertTrue(context.cancellationToken().isCancellationRequested());
                fixture.assertNoFxFailures();
                return null;
            });
        }
    }

    @Test
    public void replacingTheStoreRejectsPendingWorkEvenWhenCapturedDocumentsAreUnchanged() throws Exception {
        var releaseResult = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(releaseResult);
            return completion(context, "OLD_STORE");
        })) {
            try {
                FeatureRequestContext context = fixture.request();
                onFx(() -> {
                    fixture.editor.setDocumentStore(replacementStore(context));
                    assertTrue(context.cancellationToken().isCancellationRequested());
                    return null;
                });
                releaseResult.countDown();
                awaitWorkers(fixture.editor.worker, 0);
                onFx(() -> {
                    assertTrue(fixture.editor.candidates().isEmpty());
                    return null;
                });
            } finally {
                releaseResult.countDown();
            }
        }
    }

    @Test
    public void replacingTheStoreClearsAlreadyDisplayedCompletionState() throws Exception {
        try (Fixture fixture = open(context -> completion(context, "OLD_STORE"))) {
            FeatureRequestContext context = fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertEquals(List.of("OLD_STORE"), fixture.editor.candidates());
                fixture.editor.setDocumentStore(replacementStore(context));
                assertTrue(context.cancellationToken().isCancellationRequested());
                assertTrue(fixture.editor.candidates().isEmpty());
                assertNull(fixture.editor.activeCompletionPopup.get());
                return null;
            });
        }
    }

    @Test
    public void confirmedCloseClearsAlreadyDisplayedCompletionState() throws Exception {
        try (Fixture fixture = open(context -> completion(context, "CLOSING"))) {
            FeatureRequestContext context = fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertEquals(List.of("CLOSING"), fixture.editor.candidates());
                fixture.editor.discardChangesOnClose();
                fixture.editor.close();
                assertTrue(fixture.editor.isEditorClosed());
                assertTrue(context.cancellationToken().isCancellationRequested());
                assertTrue(fixture.editor.candidates().isEmpty());
                assertNull(fixture.editor.activeCompletionPopup.get());
                return null;
            });
        }
    }

    @Test
    public void editingImmediatelyCancelsPendingCompletion() throws Exception {
        var releaseResult = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(releaseResult);
            return completion(context, "OLD_TEXT");
        })) {
            try {
                FeatureRequestContext context = fixture.request();
                onFx(() -> {
                    fixture.editor.replaceText("class Example { int changed; }");
                    assertTrue(context.cancellationToken().isCancellationRequested());
                    return null;
                });
                releaseResult.countDown();
                awaitWorkers(fixture.editor.worker, 0);
                onFx(() -> {
                    assertTrue(fixture.editor.candidates().isEmpty());
                    return null;
                });
            } finally {
                releaseResult.countDown();
            }
        }
    }

    @Test
    public void providerFailureDoesNotPreventTheNextCompletionRequest() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            if (calls.incrementAndGet() == 1)
                throw new CompletionException(new IllegalStateException("Provider failed"));
            return completion(context, "RECOVERED");
        })) {
            fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            fixture.request();
            awaitWorkers(fixture.editor.worker, 0);
            onFx(() -> {
                assertEquals(List.of("RECOVERED"), fixture.editor.candidates());
                return null;
            });
        }
    }

    private Fixture open(Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation)
        throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, "class Example { void call() { this.; } }");
        DocumentId id = DocumentId.create();
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var provider = new ControlledProvider(computation);
        Fixture fixture = onFx(() -> {
            var failures = new LinkedBlockingQueue<Throwable>();
            Thread.UncaughtExceptionHandler previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((_, failure) -> failures.add(failure));
            Project project = new RailroadProject(directory, "Completion request", new WritableImage(1, 1));
            var pane = new TestEditor(project, id, file, new CodeEditorConfig("java", provider, null, null, null));
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            var stage = new Stage();
            stage.setTitle("Railroad completion test");
            stage.setScene(new Scene(pane, 480, 240));
            stage.show();
            pane.requestFocus();
            return new Fixture(pane, stage, provider, failures, previousHandler);
        });
        onFx(() -> null);
        return fixture;
    }

    private static FeatureResult<CompletionResult> completion(FeatureRequestContext context, String label) {
        String text = ((TextDocumentSnapshot) context.documentSnapshot()).text();
        return new FeatureResult<>(context,
            new CompletionResult(text.indexOf('.'), List.of(new CompletionItem(label, label))));
    }

    private static DefaultDocumentStore replacementStore(FeatureRequestContext context) {
        WorkspaceSnapshot workspace = context.workspaceSnapshot();
        var replacement = new DefaultDocumentStore(workspace.projectId(), workspace.configuration());
        workspace.documents().values().forEach(replacement::publishBacking);
        return replacement;
    }

    private record Fixture(
        TestEditor editor,
        Stage stage,
        ControlledProvider provider,
        BlockingQueue<Throwable> fxFailures,
        Thread.UncaughtExceptionHandler previousHandler
    ) implements AutoCloseable {
        private FeatureRequestContext request() throws Exception {
            onFx(() -> {
                editor.trigger();
                return null;
            });
            FeatureRequestContext context = provider.requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(context, "Editor did not use the request-aware completion overload");
            return context;
        }

        private void assertNoFxFailures() {
            if (!fxFailures.isEmpty())
                throw new AssertionError("Uncaught JavaFX publication failure", fxFailures.peek());
        }

        @Override
        public void close() throws Exception {
            onFx(() -> {
                try {
                    editor.discardChangesOnClose();
                    editor.close();
                    editor.dispose();
                    stage.close();
                } finally {
                    Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
                }
                return null;
            });
            assertTrue(editor.worker.awaitTermination(10, TimeUnit.SECONDS));
            assertNoFxFailures();
        }
    }

    private static final class ControlledProvider implements CompletionProvider {
        private final BlockingQueue<FeatureRequestContext> requests = new LinkedBlockingQueue<>();
        private final Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation;

        private ControlledProvider(Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation) {
            this.computation = computation;
        }

        @Override
        public CompletionResult compute(DocumentSnapshot snapshot, int triggerAt) {
            throw new AssertionError("Editor must use the request-aware completion overload");
        }

        @Override
        public FeatureResult<CompletionResult> compute(FeatureRequestContext context, int triggerAt) {
            assertSame(context.documentSnapshot(),
                context.workspaceSnapshot().document(context.documentSnapshot().id()).orElseThrow());
            assertEquals(((TextDocumentSnapshot) context.documentSnapshot()).text().indexOf('.'), triggerAt);
            requests.add(context);
            // Intentionally ignore cancellation so late-result rejection is tested in the editor.
            return computation.apply(context);
        }
    }

    private static final class TestEditor extends CodeEditorPane {
        private TestEditor(Project project, DocumentId id, Path file, CodeEditorConfig config) {
            super(project, id, file, config);
        }

        @Override
        protected boolean supportsHighlighting() {
            return false;
        }

        @Override
        protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
            return false;
        }

        private void trigger() throws ReflectiveOperationException {
            Method method = CodeEditorPane.class.getDeclaredMethod("triggerCompletion", int.class);
            method.setAccessible(true);
            method.invoke(this, getText().indexOf('.'));
        }

        private void escape() {
            fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                false, false, false, false));
        }

        private List<String> candidates() {
            return completionCandidates.stream().map(CompletionItem::insertText).toList();
        }
    }
}
