package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorSignatureRequestTest {
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
    public void acceptedHelpKeepsItsRequestActive() throws Exception {
        try (Fixture fixture = open(context -> help(context, "ACCEPTED"))) {
            Invocation invocation = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals("ACCEPTED", fixture.editor.activeSignatureHelp.get().methodName());
                assertTrue(fixture.editor.signaturePopup.isShowing());
                assertFalse(invocation.context().cancellationToken().isCancellationRequested());
                return null;
            });
        }
    }

    @Test
    public void caretMovementSupersedesWorkAtTheSameDocumentVersion() throws Exception {
        var releaseOld = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                await(releaseOld);
            }
            return help(context, call == 1 ? "OLD" : "NEW");
        })) {
            try {
                Invocation old = fixture.request();
                onFx(() -> {
                    fixture.editor.moveTo(old.caret() + 1);
                    return null;
                });
                Invocation current = fixture.nextRequest();
                assertEquals(old.caret() + 1, current.caret());
                assertSame(old.context().documentSnapshot(), current.context().documentSnapshot());
                assertNotSame(old.context(), current.context());
                assertTrue(old.context().cancellationToken().isCancellationRequested());
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals("NEW", fixture.editor.activeSignatureHelp.get().methodName());
                    return null;
                });

                releaseOld.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals("NEW", fixture.editor.activeSignatureHelp.get().methodName());
                    return null;
                });
            } finally {
                releaseOld.countDown();
            }
        }
    }

    @Test
    public void caretMovementRejectsAnOldResultAlreadyWaitingOnJavaFx() throws Exception {
        var returnOld = new CountDownLatch(1);
        var returnNew = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            int call = calls.incrementAndGet();
            await(call == 1 ? returnOld : returnNew);
            return help(context, call == 1 ? "OLD" : "NEW");
        })) {
            try {
                Invocation old = fixture.request();
                try (FxBlocker blocker = FxBlocker.start(() -> fixture.editor.moveTo(old.caret() + 1))) {
                    returnOld.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    blocker.releaseAndAwait();
                }
                fixture.nextRequest();
                onFx(() -> {
                    assertNull(fixture.editor.activeSignatureHelp.get());
                    assertTrue(old.context().cancellationToken().isCancellationRequested());
                    return null;
                });

                returnNew.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals("NEW", fixture.editor.activeSignatureHelp.get().methodName());
                    return null;
                });
            } finally {
                returnOld.countDown();
                returnNew.countDown();
            }
        }
    }

    @Test
    public void leavingAnEligibleInvocationCancelsPendingHelp() throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(release);
            return help(context, "NO_LONGER_ELIGIBLE");
        })) {
            try {
                Invocation old = fixture.request();
                onFx(() -> {
                    fixture.editor.eligible = false;
                    fixture.editor.moveTo(old.caret() + 1);
                    assertTrue(old.context().cancellationToken().isCancellationRequested());
                    return null;
                });
                release.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertNull(fixture.editor.activeSignatureHelp.get());
                    assertFalse(fixture.editor.signaturePopup.isShowing());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void dismissalRejectsAResultAlreadyWaitingOnJavaFx() throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(release);
            return help(context, "DISMISSED");
        })) {
            try {
                Invocation invocation = fixture.request();
                try (FxBlocker blocker = FxBlocker.start(fixture.editor::dismiss)) {
                    release.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                    assertNull(fixture.editor.activeSignatureHelp.get());
                    assertFalse(fixture.editor.signaturePopup.isShowing());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void equalPayloadReusesPopupContentWithoutCancellingTheNewRequest() throws Exception {
        var produced = new AtomicReference<SignatureHelp>();
        try (Fixture fixture = open(context -> {
            FeatureResult<SignatureHelp> result = help(context, "SAME");
            produced.set(result.value());
            return result;
        })) {
            Invocation first = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            SignatureHelp displayed = onFx(() -> fixture.editor.activeSignatureHelp.get());
            Object header = onFx(() -> fixture.editor.signatureTextFlow.getChildren().getFirst());

            Invocation second = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertTrue(first.context().cancellationToken().isCancellationRequested());
                assertFalse(second.context().cancellationToken().isCancellationRequested());
                assertNotSame(displayed, produced.get());
                assertEquals(displayed, produced.get());
                assertSame(displayed, fixture.editor.activeSignatureHelp.get());
                assertSame(header, fixture.editor.signatureTextFlow.getChildren().getFirst());
                assertTrue(fixture.editor.signaturePopup.isShowing());
                return null;
            });
        }
    }

    @Test
    public void unsupportedPayloadClearsPreviouslyDisplayedHelp() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> calls.incrementAndGet() == 1
            ? help(context, "PREVIOUS")
            : new FeatureResult<>(context, null))) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertNotNull(fixture.editor.activeSignatureHelp.get());
                return null;
            });

            Invocation unsupported = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertNull(fixture.editor.activeSignatureHelp.get());
                assertFalse(fixture.editor.signaturePopup.isShowing());
                assertTrue(unsupported.context().cancellationToken().isCancellationRequested());
                return null;
            });
        }
    }

    @Test
    public void aDifferentButEqualContextCannotPublishHelp() throws Exception {
        try (Fixture fixture = open(context -> {
            var different = new FeatureRequestContext(context.workspaceSnapshot(), context.documentSnapshot(),
                context.cancellationToken(), context.deadline());
            assertEquals(context, different);
            assertNotSame(context, different);
            return help(different, "WRONG_REQUEST");
        })) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertNull(fixture.editor.activeSignatureHelp.get());
                assertFalse(fixture.editor.signaturePopup.isShowing());
                return null;
            });
        }
    }

    @Test
    public void storeReplacementClearsDisplayedHelpAndCancelsTheRequest() throws Exception {
        try (Fixture fixture = open(context -> help(context, "OLD_STORE"))) {
            Invocation invocation = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertNotNull(fixture.editor.activeSignatureHelp.get());
                WorkspaceSnapshot workspace = invocation.context().workspaceSnapshot();
                var replacement = new DefaultDocumentStore(workspace.projectId(), workspace.configuration());
                workspace.documents().values().forEach(replacement::publishBacking);
                fixture.editor.setDocumentStore(replacement);
                assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                assertNull(fixture.editor.activeSignatureHelp.get());
                assertFalse(fixture.editor.signaturePopup.isShowing());
                return null;
            });
        }
    }

    @Test
    public void confirmedCloseClearsDisplayedHelpAndCancelsTheRequest() throws Exception {
        try (Fixture fixture = open(context -> help(context, "CLOSING"))) {
            Invocation invocation = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertNotNull(fixture.editor.activeSignatureHelp.get());
                fixture.editor.discardChangesOnClose();
                fixture.editor.close();
                assertTrue(fixture.editor.isEditorClosed());
                assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                assertNull(fixture.editor.activeSignatureHelp.get());
                assertFalse(fixture.editor.signaturePopup.isShowing());
                return null;
            });
        }
    }

    @Test
    public void configurationChangeCancelsPendingSignatureHelp() throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(release);
            return help(context, "OLD_CONFIGURATION");
        })) {
            try {
                Invocation invocation = fixture.request();
                onFx(() -> {
                    fixture.store.publishConfiguration(new WorkspaceConfigurationSnapshot(1, Set.of("changed")));
                    fixture.editor.refreshWorkspace();
                    assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                    return null;
                });
                release.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertNull(fixture.editor.activeSignatureHelp.get());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void editingCancelsSignatureWorkBeforeTheDebouncedReplacement() throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(release);
            return help(context, "OLD_TEXT");
        })) {
            try {
                Invocation invocation = fixture.request();
                onFx(() -> {
                    fixture.editor.eligible = false;
                    fixture.editor.replaceText("class Example { int changed; }");
                    assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                    return null;
                });
                release.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertNull(fixture.editor.activeSignatureHelp.get());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void providerFailureDoesNotPreventTheNextSignatureRequest() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            if (calls.incrementAndGet() == 1)
                throw new CompletionException(new IllegalStateException("Provider failed"));
            return help(context, "RECOVERED");
        })) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals("RECOVERED", fixture.editor.activeSignatureHelp.get().methodName());
                return null;
            });
        }
    }

    private Fixture open(Function<FeatureRequestContext, FeatureResult<SignatureHelp>> computation) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, "class Example { void run() { call(1, 2); } }");
        DocumentId id = DocumentId.create();
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var provider = new ControlledProvider(computation);
        Fixture fixture = onFx(() -> {
            var failures = new LinkedBlockingQueue<Throwable>();
            Thread.UncaughtExceptionHandler previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((_, failure) -> failures.add(failure));
            Project project = new RailroadProject(directory, "Signature request", new WritableImage(1, 1));
            var pane = new TestEditor(project, id, file,
                new CodeEditorConfig("java", null, null, provider, null, null));
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            var stage = new Stage();
            stage.setTitle("Railroad signature test");
            stage.setScene(new Scene(pane, 480, 240));
            stage.show();
            pane.moveTo(0);
            pane.requestFocus();
            return new Fixture(pane, stage, store, provider, failures, previousHandler);
        });
        onFx(() -> null);
        return fixture;
    }

    private static FeatureResult<SignatureHelp> help(FeatureRequestContext context, String name) {
        var payload = new SignatureHelp("Example", "Example", name,
            List.of(new SignatureHelp.ParameterInfo("int", "value", false)), 0, false, "void", false);
        return new FeatureResult<>(context, payload);
    }

    private record Invocation(FeatureRequestContext context, int caret) {
    }

    private record Fixture(
        TestEditor editor,
        Stage stage,
        DefaultDocumentStore store,
        ControlledProvider provider,
        BlockingQueue<Throwable> fxFailures,
        Thread.UncaughtExceptionHandler previousHandler
    ) implements AutoCloseable {
        private Invocation request() throws Exception {
            onFx(() -> {
                editor.eligible = true;
                editor.trigger();
                return null;
            });
            return nextRequest();
        }

        private Invocation nextRequest() throws InterruptedException {
            Invocation invocation = provider.requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(invocation, "Editor did not dispatch request-aware signature help");
            return invocation;
        }

        @Override
        public void close() throws Exception {
            onFx(() -> {
                try {
                    editor.eligible = false;
                    editor.discardChangesOnClose();
                    editor.close();
                    editor.dispose();
                    stage.close();
                } finally {
                    Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
                }
                return null;
            });
            CodeEditorRequestTestSupport.awaitRequests(editor.featureRequests);
            if (!fxFailures.isEmpty())
                throw new AssertionError("Uncaught JavaFX publication failure", fxFailures.peek());
        }
    }

    private static final class ControlledProvider implements SignatureHelpProvider {
        private final BlockingQueue<Invocation> requests = new LinkedBlockingQueue<>();
        private final Function<FeatureRequestContext, FeatureResult<SignatureHelp>> computation;

        private ControlledProvider(Function<FeatureRequestContext, FeatureResult<SignatureHelp>> computation) {
            this.computation = computation;
        }

        @Override
        public SignatureHelp compute(DocumentSnapshot snapshot, int caretPosition) {
            throw new AssertionError("Editor must use the request-aware signature overload");
        }

        @Override
        public FeatureResult<SignatureHelp> compute(FeatureRequestContext context, int caretPosition) {
            assertFalse(Platform.isFxApplicationThread());
            requests.add(new Invocation(context, caretPosition));
            // Intentionally ignore cancellation to test the editor's publication boundary.
            return computation.apply(context);
        }
    }

    private static final class TestEditor extends CodeEditorPane {
        private boolean eligible;

        private TestEditor(Project project, DocumentId id, Path file, CodeEditorConfig config) {
            super(project, id, file, config);
        }

        @Override
        protected boolean supportsHighlighting() {
            return false;
        }

        @Override
        protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
            return eligible;
        }

        private void trigger() {
            invoke("requestSignatureHelp", new Class<?>[]{boolean.class}, new Object[]{false});
        }

        private void dismiss() {
            invoke("hideSignatureHelp", new Class<?>[0], new Object[0]);
        }

        private void refreshWorkspace() {
            invoke("refreshWorkspaceFeatures", new Class<?>[0], new Object[0]);
        }

        private void invoke(String name, Class<?>[] parameterTypes, Object[] arguments) {
            try {
                Method method = CodeEditorPane.class.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                method.invoke(this, arguments);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
