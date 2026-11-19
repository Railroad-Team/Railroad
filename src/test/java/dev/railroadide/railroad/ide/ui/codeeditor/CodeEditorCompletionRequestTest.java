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
import java.util.function.Predicate;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ide.sst.provider.api.CapabilityRegistry;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageSession;

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
            awaitRequests(fixture.editor.featureRequests);
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
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.candidates());
                    return null;
                });

                releaseOld.countDown();
                awaitRequests(fixture.editor.featureRequests);
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
                        awaitRequests(fixture.editor.featureRequests);
                        blocker.releaseAndAwait();
                    }
                } else {
                    onFx(() -> {
                        fixture.editor.escape();
                        return null;
                    });
                    releaseResult.countDown();
                    awaitRequests(fixture.editor.featureRequests);
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
            awaitRequests(fixture.editor.featureRequests);
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
            awaitRequests(fixture.editor.featureRequests);
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
            awaitRequests(fixture.editor.featureRequests);
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
                awaitRequests(fixture.editor.featureRequests);
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
            awaitRequests(fixture.editor.featureRequests);
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
            awaitRequests(fixture.editor.featureRequests);
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
                awaitRequests(fixture.editor.featureRequests);
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
            awaitRequests(fixture.editor.featureRequests);
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(List.of("RECOVERED"), fixture.editor.candidates());
                return null;
            });
        }
    }

    @Test
    public void unloadedProviderCannotPublishAResultAlreadyQueuedForTheEditor() throws Exception {
        var releaseResult = new CountDownLatch(1);
        try (var session = new LanguageSession(
            CapabilityRegistry.empty());
            Fixture fixture = open(context -> {
                await(releaseResult);
                return completion(context, "UNLOADED");
            }, "this.", text -> text.endsWith("."), session.cancellationToken())) {
            try {
                FeatureRequestContext request = fixture.request();
                try (FxBlocker blocker = FxBlocker.start(() -> {
                })) {
                    releaseResult.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    assertFalse(request.cancellationToken().isCancellationRequested());
                    session.close();
                    assertTrue(request.cancellationToken().isCancellationRequested());
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(fixture.editor.candidates().isEmpty());
                    assertNull(fixture.editor.activeCompletionPopup.get());
                    return null;
                });
            } finally {
                releaseResult.countDown();
            }
        }
    }

    @Test
    public void displayedCompletionCannotBeAcceptedAfterProviderUnload() throws Exception {
        try (var session = new LanguageSession(
            CapabilityRegistry.empty());
            Fixture fixture = open(context -> completion(context, "UNLOADED"), "this.",
                text -> text.endsWith("."), session.cancellationToken())) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(List.of("UNLOADED"), fixture.editor.candidates());
                session.close();
                var accept = CodeEditorPane.class.getDeclaredMethod("completeFromSelection", RRListView.class);
                accept.setAccessible(true);
                accept.invoke(fixture.editor, fixture.editor.activeCompletionList.get());
                assertEquals("this.", fixture.editor.getText());
                assertNull(fixture.editor.activeCompletionPopup.get());
                return null;
            });
        }
    }

    @Test
    public void providerCanCompleteAfterColonWithoutEditorLanguageRules() throws Exception {
        assertCustomCompletion("name", ":", 5, "name:value", text -> text.endsWith(":"));
    }

    @Test
    public void providerCanReplaceAWordPrefixStartingAtZero() throws Exception {
        assertCustomCompletion("", "va", 0, "value", text -> !text.isEmpty());
    }

    private void assertCustomCompletion(
        String initial,
        String inserted,
        int replacementStart,
        String expected,
        Predicate<String> policy
    ) throws Exception {
        try (Fixture fixture = open(context -> new FeatureResult<>(context,
            new CompletionResult(replacementStart, List.of(new CompletionItem("value", "value")))), initial, policy)) {
            onFx(() -> {
                fixture.editor.moveTo(fixture.editor.getLength());
                fixture.editor.insertText(fixture.editor.getLength(), inserted);
                fixture.editor.moveTo(fixture.editor.getLength());
                return null;
            });
            assertNotNull(fixture.provider.requests.poll(10, TimeUnit.SECONDS), "Custom trigger was not dispatched");
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(List.of("value"), fixture.editor.candidates());
                var filter = CodeEditorPane.class.getDeclaredMethod("filterAutoComplete", String.class);
                filter.setAccessible(true);
                filter.invoke(fixture.editor, fixture.editor.getText());
                assertNotNull(fixture.editor.activeCompletionList.get());
                var accept = CodeEditorPane.class.getDeclaredMethod("completeFromSelection",
                    RRListView.class);
                accept.setAccessible(true);
                accept.invoke(fixture.editor, fixture.editor.activeCompletionList.get());
                assertEquals(expected, fixture.editor.getText());
                return null;
            });
        }
    }

    private Fixture open(Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation)
        throws Exception {
        return open(computation, "class Example { void call() { this.; } }", text -> text.endsWith("."));
    }

    private Fixture open(
        Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation,
        String initialText,
        Predicate<String> triggerPolicy
    ) throws Exception {
        return open(computation, initialText, triggerPolicy, CancellationToken.none());
    }

    private Fixture open(
        Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation,
        String initialText,
        Predicate<String> triggerPolicy,
        CancellationToken providerLifetime
    ) throws Exception {
        Path file = directory.resolve("Example.txt");
        Files.writeString(file, initialText);
        DocumentId id = DocumentId.create();
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var provider = new ControlledProvider(computation, triggerPolicy);
        Fixture fixture = onFx(() -> {
            var failures = new LinkedBlockingQueue<Throwable>();
            Thread.UncaughtExceptionHandler previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((_, failure) -> failures.add(failure));
            Project project = new RailroadProject(directory, "Completion request", new WritableImage(1, 1));
            var pane = new TestEditor(project, id, file,
                new CodeEditorConfig("java", provider, null, null, null, null, providerLifetime));
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
            new CompletionResult(text.indexOf('.') + 1, List.of(new CompletionItem(label, label))));
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
            CodeEditorRequestTestSupport.awaitRequests(editor.featureRequests);
            assertNoFxFailures();
        }
    }

    private static final class ControlledProvider implements CompletionProvider {
        private final BlockingQueue<FeatureRequestContext> requests = new LinkedBlockingQueue<>();
        private final Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation;
        private final Predicate<String> triggerPolicy;

        private ControlledProvider(
            Function<FeatureRequestContext, FeatureResult<CompletionResult>> computation,
            Predicate<String> triggerPolicy
        ) {
            this.computation = computation;
            this.triggerPolicy = triggerPolicy;
        }

        @Override
        public boolean shouldTrigger(String insertedText) {
            return triggerPolicy.test(insertedText);
        }

        @Override
        public CompletionResult compute(DocumentSnapshot snapshot, int triggerAt) {
            throw new AssertionError("Editor must use the request-aware completion overload");
        }

        @Override
        public FeatureResult<CompletionResult> compute(FeatureRequestContext context, int triggerAt) {
            assertSame(context.documentSnapshot(),
                context.workspaceSnapshot().document(context.documentSnapshot().id()).orElseThrow());
            String text = ((TextDocumentSnapshot) context.documentSnapshot()).text();
            assertTrue(triggerAt >= 0 && triggerAt < text.length());
            assertTrue(triggerPolicy.test(text.substring(triggerAt, triggerAt + 1)));
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
            moveTo(getText().indexOf('.') + 1);
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
