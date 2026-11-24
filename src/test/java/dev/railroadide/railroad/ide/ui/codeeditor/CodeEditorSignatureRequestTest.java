package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.Collections;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorSignatureRequestTest {
    @TempDir
    public Path directory;
    private Boolean previousAutomaticHints;

    @BeforeAll
    public static void initializeFx() throws Exception {
        startFx();
        onFx(() -> {
            Platform.setImplicitExit(false);
            Keybinds.initialize();
            return null;
        });
    }

    @BeforeEach
    public void enableAutomaticHintsForEditorLifecycleTests() {
        previousAutomaticHints = Settings.AUTOMATIC_PARAMETER_HINTS.getValue();
        Settings.AUTOMATIC_PARAMETER_HINTS.setValue(true);
    }

    @AfterEach
    public void restoreAutomaticHints() {
        Settings.AUTOMATIC_PARAMETER_HINTS.setValue(previousAutomaticHints);
    }

    @Test
    public void controlPShowsAndReopensParameterInfoWithoutTypingOrMovingTheCaret() throws Exception {
        try (Fixture fixture = open(context -> new FeatureResult<>(context, new SignatureHelp("Example", "Example",
            "call", List.of(new SignatureHelp.ParameterInfo("int", "first", false),
                new SignatureHelp.ParameterInfo("int", "second", false)),
            1, false, "void", false)))) {
            var editor = fixture.editor;
            fixture.provider.overloads = List.of(
                new SignatureHelp("Example", "Example", "call",
                    List.of(new SignatureHelp.ParameterInfo("long", "count", false)), -1, false, "void", false),
                new SignatureHelp("Example", "Example", "call", List.of(), -1, false, "void", false));
            String text = onFx(editor::getText);
            int caret = text.indexOf(", ") + 2;
            onFx(() -> {
                assertFalse(Settings.AUTOMATIC_PARAMETER_HINTS.getDefaultValue());
                Settings.AUTOMATIC_PARAMETER_HINTS.setValue(Settings.AUTOMATIC_PARAMETER_HINTS.getDefaultValue());
                editor.eligible = true;
                editor.moveTo(caret);
                assertFalse(editor.signaturePopup.isShowing());
                return null;
            });
            awaitRequests(editor.featureRequests);
            assertTrue(fixture.provider.requests.isEmpty());
            for (int attempt = 0; attempt < 2; attempt++) {
                onFx(() -> {
                    editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.P, false, true, false, false));
                    return null;
                });
                assertEquals(caret, fixture.nextRequest().caret());
                awaitRequests(editor.featureRequests);
                onFx(() -> {
                    assertTrue(editor.signaturePopup.isShowing());
                    assertEquals(
                        List.of(List.of("int first", ", ", "int second"), List.of("long count"), List.of("()")),
                        editor.signatureRows.getChildren().stream().map(node -> ((TextFlow) node).getChildren()
                            .stream().map(part -> ((Text) part).getText()).toList()).toList());
                    assertEquals(1, editor.signatureRows.lookupAll(".signature-param-active").size());
                    assertEquals("int second",
                        ((Text) editor.signatureRows.lookup(".signature-param-active")).getText());
                    assertEquals(text, editor.getText());
                    assertEquals(caret, editor.getCaretPosition());
                    editor.dismiss();
                    return null;
                });
            }
        }
    }

    @Test
    public void parameterPopupKeepsLongRowsOnOneLineWhenTheScreenHasRoom() throws Exception {
        String name = "longParameterName".repeat(8);
        var signature = new SignatureHelp("Example", "Example", "call",
            List.of(new SignatureHelp.ParameterInfo("int", name, false)), 0, false, "void", false);
        try (Fixture fixture = open(context -> new FeatureResult<>(context, signature))) {
            for (int overloadCount : List.of(0, 8)) {
                fixture.provider.overloads = Collections.nCopies(overloadCount, signature);
                onFx(() -> {
                    fixture.editor.showParameterInfo();
                    return null;
                });
                fixture.nextRequest();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    var popup = fixture.editor.signaturePopup;
                    var root = popup.getScene().getRoot();
                    root.applyCss();
                    root.layout();
                    var row = (TextFlow) fixture.editor.signatureRows.getChildren().getFirst();
                    var text = (Text) row.getChildren().getFirst();
                    double screenWidth = Screen.getScreensForRectangle(fixture.stage.getX(), fixture.stage.getY(), 1, 1)
                        .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds().getWidth();
                    double textWidth = text.getLayoutBounds().getWidth();
                    if (screenWidth > textWidth + 60) {
                        assertTrue(row.getWidth() >= Math.floor(textWidth));
                        assertEquals(text.getLayoutBounds().getHeight(), row.getHeight(), 2);
                    }
                    assertTrue(popup.getWidth() <= screenWidth);
                    return null;
                });
            }
        }
    }

    @Test
    public void hidesParameterInfoWhenTheCallHasNoParameters() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> calls.getAndIncrement() == 0
            ? help(context, "withParameters")
            : new FeatureResult<>(context, new SignatureHelp("Example", "Example", "noParameters", List.of(),
                -1, false, "void", false)))) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            assertTrue(onFx(fixture.editor.signaturePopup::isShowing));

            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            assertFalse(onFx(fixture.editor.signaturePopup::isShowing));
        }
    }

    @Test
    public void acceptedHelpKeepsItsRequestActive() throws Exception {
        try (Fixture fixture = open(context -> help(context, "ACCEPTED"))) {
            Invocation invocation = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals("ACCEPTED", fixture.editor.activeSignatureHelp.get().getFirst().methodName());
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
                    assertEquals("NEW", fixture.editor.activeSignatureHelp.get().getFirst().methodName());
                    return null;
                });

                releaseOld.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals("NEW", fixture.editor.activeSignatureHelp.get().getFirst().methodName());
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
                    assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
                    assertTrue(old.context().cancellationToken().isCancellationRequested());
                    return null;
                });

                returnNew.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals("NEW", fixture.editor.activeSignatureHelp.get().getFirst().methodName());
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
                    assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                    assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
            SignatureHelp displayed = onFx(() -> fixture.editor.activeSignatureHelp.get().getFirst());
            Object parameter = onFx(() -> fixture.editor.signatureRows.getChildren().getFirst());

            Invocation second = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertTrue(first.context().cancellationToken().isCancellationRequested());
                assertFalse(second.context().cancellationToken().isCancellationRequested());
                assertNotSame(displayed, produced.get());
                assertEquals(displayed, produced.get());
                assertSame(displayed, fixture.editor.activeSignatureHelp.get().getFirst());
                assertSame(parameter, fixture.editor.signatureRows.getChildren().getFirst());
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
                assertFalse(fixture.editor.activeSignatureHelp.get().isEmpty());
                return null;
            });

            Invocation unsupported = fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                assertFalse(fixture.editor.activeSignatureHelp.get().isEmpty());
                WorkspaceSnapshot workspace = invocation.context().workspaceSnapshot();
                var replacement = new DefaultDocumentStore(workspace.projectId(), workspace.configuration());
                workspace.documents().values().forEach(replacement::publishBacking);
                fixture.editor.setDocumentStore(replacement);
                assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                assertFalse(fixture.editor.activeSignatureHelp.get().isEmpty());
                fixture.editor.discardChangesOnClose();
                fixture.editor.close();
                assertTrue(fixture.editor.isEditorClosed());
                assertTrue(invocation.context().cancellationToken().isCancellationRequested());
                assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                    assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                    assertTrue(fixture.editor.activeSignatureHelp.get().isEmpty());
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
                assertEquals("RECOVERED", fixture.editor.activeSignatureHelp.get().getFirst().methodName());
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
        private List<SignatureHelp> overloads = List.of();

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

        @Override
        public FeatureResult<List<SignatureHelp>> compute(
            FeatureRequestContext context,
            int caretPosition,
            boolean includeOverloads
        ) {
            var result = SignatureHelpProvider.super.compute(context, caretPosition, includeOverloads);
            if (!includeOverloads || overloads.isEmpty())
                return result;
            var signatures = new ArrayList<>(result.value());
            signatures.addAll(overloads);
            return new FeatureResult<>(result.context(), List.copyOf(signatures));
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
