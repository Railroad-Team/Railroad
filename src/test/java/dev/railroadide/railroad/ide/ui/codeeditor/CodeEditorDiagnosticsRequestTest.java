package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorDiagnosticsRequestTest {
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
    public void supersedingTheRequestCancelsItsTokenAndRejectsItsLateResult() throws Exception {
        assertSupersession(false);
    }

    @Test
    public void replacingTheDocumentStoreCancelsWorkCapturedFromThePreviousStore() throws Exception {
        assertSupersession(true);
    }

    private void assertSupersession(boolean replaceStore) throws Exception {
        var releaseOld = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                await(releaseOld);
            }
            return diagnostics(context, call == 1 ? "OLD" : "NEW");
        })) {
            try {
                FeatureRequestContext old = fixture.nextRequest();
                onFx(() -> {
                    if (replaceStore) {
                        var replacement = new DefaultDocumentStore("test", fixture.store.capture().configuration());
                        replacement.publishBacking(old.documentSnapshot());
                        fixture.editor.setDocumentStore(replacement);
                    } else {
                        fixture.editor.request();
                    }
                    assertTrue(old.cancellationToken().isCancellationRequested());
                    return null;
                });
                FeatureRequestContext current = fixture.nextRequest();
                assertNotSame(old, current);
                assertSame(old.documentSnapshot(), current.documentSnapshot());
                assertFalse(current.cancellationToken().isCancellationRequested());

                fixture.editor.awaitRequests();
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.diagnosticCodes());
                    return null;
                });

                // The old provider deliberately ignores cancellation and completes last.
                releaseOld.countDown();
                fixture.editor.awaitRequests();
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.diagnosticCodes());
                    return null;
                });
            } finally {
                releaseOld.countDown();
            }
        }
    }

    @Test
    public void configurationChangeCancelsPendingDiagnosticsBeforeDebounceFinishes() throws Exception {
        assertWorkspaceInvalidation("configuration");
    }

    @Test
    public void dependencyChangeCancelsPendingDiagnosticsBeforeDebounceFinishes() throws Exception {
        assertWorkspaceInvalidation("dependency");
    }

    @Test
    public void renameAtTheSameContentVersionCancelsPendingDiagnostics() throws Exception {
        assertWorkspaceInvalidation("rename");
    }

    private void assertWorkspaceInvalidation(String change) throws Exception {
        var releaseOld = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(releaseOld);
            return diagnostics(context, "OLD");
        })) {
            try {
                FeatureRequestContext captured = fixture.nextRequest();
                onFx(() -> {
                    if (change.equals("configuration")) {
                        fixture.store.publishConfiguration(new WorkspaceConfigurationSnapshot(1, Set.of("changed")));
                    } else if (change.equals("dependency")) {
                        TextDocumentSnapshot dependency = fixture.dependency;
                        fixture.store.publishBacking(new TextDocumentSnapshot(dependency.id(), dependency.uri(),
                            dependency.version().next(), "java", "changed dependency", StandardCharsets.UTF_8));
                    } else {
                        fixture.store.relocate(captured.documentSnapshot().id(),
                            DocumentUri.fromPath(directory.resolve("Renamed.java")));
                        assertEquals(captured.documentSnapshot().version(),
                            fixture.store.capture().document(captured.documentSnapshot().id()).orElseThrow().version());
                    }
                    fixture.editor.refreshWorkspace();
                    fixture.editor.pauseWorkspaceDebounce();
                    assertTrue(captured.cancellationToken().isCancellationRequested());
                    assertTrue(fixture.provider.requests.isEmpty(), "Replacement must wait for the debounce");
                    return null;
                });

                releaseOld.countDown();
                fixture.editor.awaitRequests();
                onFx(() -> {
                    assertTrue(fixture.editor.diagnosticCodes().isEmpty());
                    return null;
                });
            } finally {
                releaseOld.countDown();
            }
        }
    }

    @Test
    public void editingRejectsAResultAlreadyWaitingInTheFxQueue() throws Exception {
        var returnResult = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(returnResult);
            return diagnostics(context, "QUEUED");
        })) {
            try {
                FeatureRequestContext captured = fixture.nextRequest();
                try (FxBlocker blocker = FxBlocker.start(() -> {
                    fixture.editor.enabled = false;
                    fixture.editor.replaceText("class Example { int edited; }");
                    assertTrue(captured.cancellationToken().isCancellationRequested());
                })) {
                    returnResult.countDown();
                    fixture.editor.awaitRequests();
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(fixture.editor.diagnosticCodes().isEmpty());
                    return null;
                });
            } finally {
                returnResult.countDown();
            }
        }
    }

    @Test
    public void aNewRequestInvalidatesAnOldResultAlreadyWaitingInTheFxQueue() throws Exception {
        var returnOld = new CountDownLatch(1);
        var returnNew = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            int call = calls.incrementAndGet();
            await(call == 1 ? returnOld : returnNew);
            return diagnostics(context, call == 1 ? "OLD" : "NEW");
        })) {
            try {
                FeatureRequestContext old = fixture.nextRequest();
                try (FxBlocker blocker = FxBlocker.start(fixture.editor::request)) {
                    returnOld.countDown();
                    fixture.editor.awaitRequests();
                    blocker.releaseAndAwait();
                }
                FeatureRequestContext current = fixture.nextRequest();
                assertTrue(old.cancellationToken().isCancellationRequested());
                assertNotSame(old, current);
                onFx(() -> {
                    assertTrue(fixture.editor.diagnosticCodes().isEmpty(), "Queued old result must not be applied");
                    return null;
                });

                returnNew.countDown();
                fixture.editor.awaitRequests();
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.diagnosticCodes());
                    return null;
                });
            } finally {
                returnOld.countDown();
                returnNew.countDown();
            }
        }
    }

    @Test
    public void closingRejectsAResultAlreadyWaitingInTheFxQueue() throws Exception {
        var returnResult = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(returnResult);
            return diagnostics(context, "QUEUED");
        })) {
            try {
                FeatureRequestContext captured = fixture.nextRequest();
                try (FxBlocker blocker = FxBlocker.start(() -> {
                    fixture.editor.discardChangesOnClose();
                    fixture.editor.close();
                    assertTrue(captured.cancellationToken().isCancellationRequested());
                })) {
                    returnResult.countDown();
                    fixture.editor.awaitRequests();
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(fixture.editor.isEditorClosed());
                    assertTrue(fixture.editor.diagnosticCodes().isEmpty());
                    return null;
                });
            } finally {
                returnResult.countDown();
            }
        }
    }

    @Test
    public void closingCancelsTheRequestBeforeInterruptingItsWorker() throws Exception {
        var block = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var cancelledAtInterrupt = new AtomicBoolean();
        try (Fixture fixture = open(context -> {
            try {
                assertTrue(block.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                cancelledAtInterrupt.set(context.cancellationToken().isCancellationRequested());
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CancellationException("Editor closed");
            }
            return diagnostics(context, "FINISHED");
        })) {
            try {
                fixture.nextRequest();
                onFx(() -> {
                    fixture.editor.discardChangesOnClose();
                    fixture.editor.close();
                    return null;
                });

                assertTrue(interrupted.await(10, TimeUnit.SECONDS));
                assertTrue(cancelledAtInterrupt.get());
            } finally {
                block.countDown();
            }
        }
    }

    @Test
    public void aProviderCannotPublishUsingADifferentButEqualRequestContext() throws Exception {
        try (Fixture fixture = open(context -> {
            var different = new FeatureRequestContext(context.workspaceSnapshot(), context.documentSnapshot(),
                context.cancellationToken(), context.deadline());
            assertNotSame(context, different);
            assertEquals(context, different);
            return diagnostics(different, "WRONG_REQUEST");
        })) {
            fixture.nextRequest();
            fixture.editor.awaitRequests();
            onFx(() -> {
                assertTrue(fixture.editor.diagnosticCodes().isEmpty());
                return null;
            });
        }
    }

    @Test
    public void aProviderFailureDoesNotPreventTheNextRequestFromPublishing() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            if (calls.incrementAndGet() == 1)
                throw new CompletionException(new ExecutionException(new IllegalStateException("Provider failed")));
            return diagnostics(context, "RECOVERED");
        })) {
            fixture.nextRequest();
            fixture.editor.awaitRequests();
            onFx(() -> {
                fixture.editor.request();
                return null;
            });
            fixture.nextRequest();
            fixture.editor.awaitRequests();
            onFx(() -> {
                assertEquals(List.of("RECOVERED"), fixture.editor.diagnosticCodes());
                return null;
            });
        }
    }

    private Fixture open(Function<FeatureRequestContext, FeatureResult<List<TextEditorDiagnostic>>> computation)
        throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, "class Example {}");
        DocumentId id = DocumentId.create();
        DocumentId dependencyId = DocumentId.create();
        var dependency = new TextDocumentSnapshot(dependencyId, DocumentUri.inMemory(dependencyId),
            DocumentVersion.initial(), "java", "dependency", StandardCharsets.UTF_8);
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        store.publishBacking(dependency);
        var provider = new ControlledProvider(computation);
        TestEditor editor = onFx(() -> {
            Project project = new RailroadProject(directory, "Diagnostics request", new WritableImage(1, 1));
            var pane = new TestEditor(project, id, file,
                new CodeEditorConfig("java", null, provider, null, null, null));
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            return pane;
        });
        return new Fixture(editor, store, dependency, provider);
    }

    private static FeatureResult<List<TextEditorDiagnostic>> diagnostics(FeatureRequestContext context, String code) {
        return new FeatureResult<>(context, List.of(new TextEditorDiagnostic(
            Location.TextLocation.from((TextDocumentSnapshot) context.documentSnapshot(), 0, 1),
            DiagnosticSeverity.ERROR, code, code)));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Test did not release the coordinated operation");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Test operation interrupted");
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private record Fixture(
        TestEditor editor,
        DefaultDocumentStore store,
        TextDocumentSnapshot dependency,
        ControlledProvider provider
    ) implements AutoCloseable {
        private FeatureRequestContext nextRequest() throws InterruptedException {
            FeatureRequestContext context = provider.requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(context, "Editor did not dispatch a request-aware diagnostics call");
            return context;
        }

        @Override
        public void close() throws Exception {
            onFx(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
            CodeEditorRequestTestSupport.awaitRequests(editor.featureRequests);
        }
    }

    private static final class ControlledProvider implements DiagnosticsProvider<TextEditorDiagnostic> {
        private final BlockingQueue<FeatureRequestContext> requests = new LinkedBlockingQueue<>();
        private final Function<FeatureRequestContext, FeatureResult<List<TextEditorDiagnostic>>> computation;

        private ControlledProvider(
            Function<FeatureRequestContext, FeatureResult<List<TextEditorDiagnostic>>> computation
        ) {
            this.computation = computation;
        }

        @Override
        public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
            throw new AssertionError("Editor must dispatch through the request-aware overload");
        }

        @Override
        public FeatureResult<List<TextEditorDiagnostic>> compute(FeatureRequestContext context) {
            requests.add(context);
            // Deliberately bypass cooperative checks to exercise the editor's publication guard.
            return computation.apply(context);
        }
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
        protected boolean supportsHighlighting() {
            return false;
        }

        @Override
        protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
            return false;
        }

        private void request() {
            invoke("requestDiagnostics");
        }

        private void refreshWorkspace() {
            invoke("refreshWorkspaceFeatures");
        }

        private void invoke(String name) {
            try {
                Method method = CodeEditorPane.class.getDeclaredMethod(name);
                method.setAccessible(true);
                method.invoke(this);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }

        private void pauseWorkspaceDebounce() throws ReflectiveOperationException {
            Field field = CodeEditorPane.class.getDeclaredField("workspaceDiagnosticsDelay");
            field.setAccessible(true);
            ((PauseTransition) field.get(this)).pause();
        }

        private List<String> diagnosticCodes() {
            return visibleDiagnostics.stream().map(TextEditorDiagnostic::code).toList();
        }

        private void awaitRequests() throws InterruptedException {
            CodeEditorRequestTestSupport.awaitRequests(featureRequests);
        }

    }

    private static final class FxBlocker implements AutoCloseable {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CompletableFuture<Void> finished = new CompletableFuture<>();

        private static FxBlocker start(Runnable beforeQueuedPublications) throws InterruptedException {
            var blocker = new FxBlocker();
            Platform.runLater(() -> {
                blocker.entered.countDown();
                try {
                    await(blocker.release);
                    beforeQueuedPublications.run();
                    blocker.finished.complete(null);
                } catch (Throwable failure) {
                    blocker.finished.completeExceptionally(failure);
                }
            });
            assertTrue(blocker.entered.await(10, TimeUnit.SECONDS));
            return blocker;
        }

        private void releaseAndAwait() throws Exception {
            release.countDown();
            finished.get(10, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            releaseAndAwait();
        }
    }
}
