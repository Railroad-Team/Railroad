package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorHighlightingRequestTest {
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
    public void initialHighlightingWaitsForStoreAttachmentAndUsesItsExactSnapshot() throws Exception {
        try (Fixture fixture = open(context -> styles(context, "INITIAL"), false, "class Example {}")) {
            awaitRequests(fixture.editor.featureRequests);
            assertTrue(fixture.provider.requests.isEmpty(),
                "No request has coherent workspace inputs before attachment");
            onFx(() -> {
                fixture.editor.setDocumentStore(fixture.store);
                return null;
            });
            FeatureRequestContext context = fixture.nextRequest();
            assertSame(fixture.store.capture().document(context.documentSnapshot().id()).orElseThrow(),
                context.documentSnapshot());
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(List.of("INITIAL"), fixture.editor.highlightLabels());
                assertFalse(context.cancellationToken().isCancellationRequested());
                return null;
            });
        }
    }

    @Test
    public void aSupersededRequestCannotOverwriteNewerStyles() throws Exception {
        assertSupersession(false);
    }

    @Test
    public void replacingTheStoreRestartsHighlightingAndRejectsTheOldResult() throws Exception {
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
            return styles(context, call == 1 ? "OLD" : "NEW");
        })) {
            try {
                FeatureRequestContext old = fixture.nextRequest();
                onFx(() -> {
                    if (replaceStore) {
                        WorkspaceSnapshot workspace = old.workspaceSnapshot();
                        var replacement = new DefaultDocumentStore(workspace.projectId(), workspace.configuration());
                        workspace.documents().values().forEach(replacement::publishBacking);
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
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.highlightLabels());
                    return null;
                });

                releaseOld.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals(List.of("NEW"), fixture.editor.highlightLabels());
                    return null;
                });
            } finally {
                releaseOld.countDown();
            }
        }
    }

    @Test
    public void editingCancelsWorkImmediatelyDuringTheHighlightDebounce() throws Exception {
        assertEditRejectsHighlight(false);
    }

    @Test
    public void editingRejectsStylesAlreadyWaitingInTheFxQueue() throws Exception {
        assertEditRejectsHighlight(true);
    }

    private void assertEditRejectsHighlight(boolean alreadyQueued) throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(release);
            return styles(context, "OLD_TEXT");
        })) {
            try {
                FeatureRequestContext context = fixture.nextRequest();
                Runnable edit = () -> {
                    fixture.editor.highlightingDisabled = true;
                    fixture.editor.replaceText("class Example { int edited; }");
                    assertTrue(context.cancellationToken().isCancellationRequested());
                };
                if (alreadyQueued) {
                    try (FxBlocker blocker = FxBlocker.start(edit)) {
                        release.countDown();
                        awaitRequests(fixture.editor.featureRequests);
                        blocker.releaseAndAwait();
                    }
                } else {
                    onFx(() -> {
                        edit.run();
                        return null;
                    });
                    release.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                }
                onFx(() -> {
                    assertTrue(fixture.editor.highlightLabels().isEmpty());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void configurationChangeRetriesHighlightingWithoutATextEdit() throws Exception {
        assertWorkspaceRetry("configuration");
    }

    @Test
    public void dependencyChangeRetriesHighlightingWithoutATextEdit() throws Exception {
        assertWorkspaceRetry("dependency");
    }

    @Test
    public void renameAtTheSameContentVersionRetriesHighlighting() throws Exception {
        assertWorkspaceRetry("rename");
    }

    private void assertWorkspaceRetry(String change) throws Exception {
        var releaseOld = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                await(releaseOld);
            }
            return styles(context, call == 1 ? "OLD_WORKSPACE" : "NEW_WORKSPACE");
        })) {
            try {
                FeatureRequestContext old = fixture.nextRequest();
                onFx(() -> {
                    if (change.equals("configuration")) {
                        fixture.store.publishConfiguration(new WorkspaceConfigurationSnapshot(1, Set.of("changed")));
                    } else if (change.equals("dependency")) {
                        TextDocumentSnapshot dependency = fixture.dependency;
                        fixture.store.publishBacking(new TextDocumentSnapshot(dependency.id(), dependency.uri(),
                            dependency.version().next(), "java", "updated dependency", dependency.encoding()));
                    } else {
                        fixture.store.relocate(old.documentSnapshot().id(),
                            DocumentUri.fromPath(directory.resolve("Renamed.java")));
                    }
                    fixture.editor.refreshWorkspace();
                    assertTrue(old.cancellationToken().isCancellationRequested());
                    return null;
                });
                FeatureRequestContext current = fixture.nextRequest();
                assertEquals(old.documentSnapshot().version(), current.documentSnapshot().version());
                assertEquals(((TextDocumentSnapshot) old.documentSnapshot()).text(),
                    ((TextDocumentSnapshot) current.documentSnapshot()).text());
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals(List.of("NEW_WORKSPACE"), fixture.editor.highlightLabels());
                    return null;
                });

                releaseOld.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals(List.of("NEW_WORKSPACE"), fixture.editor.highlightLabels());
                    return null;
                });
            } finally {
                releaseOld.countDown();
            }
        }
    }

    @Test
    public void aDifferentButEqualRequestContextCannotPublishStyles() throws Exception {
        try (Fixture fixture = open(context -> {
            var different = new FeatureRequestContext(context.workspaceSnapshot(), context.documentSnapshot(),
                context.cancellationToken(), context.deadline());
            assertEquals(context, different);
            assertNotSame(context, different);
            return styles(different, "WRONG_REQUEST");
        })) {
            fixture.nextRequest();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertTrue(fixture.editor.highlightLabels().isEmpty());
                return null;
            });
        }
    }

    @Test
    public void closingRejectsStylesAlreadyWaitingInTheFxQueue() throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open(context -> {
            await(release);
            return styles(context, "CLOSED");
        })) {
            try {
                FeatureRequestContext context = fixture.nextRequest();
                try (FxBlocker blocker = FxBlocker.start(() -> {
                    fixture.editor.discardChangesOnClose();
                    fixture.editor.close();
                    assertTrue(context.cancellationToken().isCancellationRequested());
                })) {
                    release.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(fixture.editor.isEditorClosed());
                    assertTrue(fixture.editor.highlightLabels().isEmpty());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void providerFailureDoesNotPreventANewerHighlightingRequest() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open(context -> {
            if (calls.incrementAndGet() == 1)
                throw new CompletionException(new IllegalStateException("Provider failed"));
            return styles(context, "RECOVERED");
        })) {
            fixture.nextRequest();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                fixture.editor.request();
                return null;
            });
            fixture.nextRequest();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(List.of("RECOVERED"), fixture.editor.highlightLabels());
                return null;
            });
        }
    }

    @Test
    public void anEmptyDocumentCanReceiveItsInitialStyles() throws Exception {
        try (Fixture fixture = open(context -> styles(context, "EMPTY"), true, "")) {
            fixture.nextRequest();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(0, fixture.editor.lastHighlight.length());
                assertEquals(List.of("EMPTY"), fixture.editor.highlightLabels());
                return null;
            });
        }
    }

    private Fixture open(Function<FeatureRequestContext, FeatureResult<StyleSpans<Collection<String>>>> computation)
        throws Exception {
        return open(computation, true, "class Example {}");
    }

    private Fixture open(
        Function<FeatureRequestContext, FeatureResult<StyleSpans<Collection<String>>>> computation,
        boolean attach,
        String text
    ) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, text);
        DocumentId id = DocumentId.create();
        DocumentId dependencyId = DocumentId.create();
        var dependency = new TextDocumentSnapshot(dependencyId, DocumentUri.inMemory(dependencyId),
            DocumentVersion.initial(), "java", "dependency", StandardCharsets.UTF_8);
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        store.publishBacking(dependency);
        var provider = new ControlledProvider(computation);
        return onFx(() -> {
            var failures = new LinkedBlockingQueue<Throwable>();
            Thread.UncaughtExceptionHandler previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((_, failure) -> failures.add(failure));
            Project project = new RailroadProject(directory, "Highlighting request", new WritableImage(1, 1));
            var pane = new TestEditor(project, id, file,
                new CodeEditorConfig("java", null, null, null, null, provider));
            store.publishBacking(pane.captureSnapshot());
            if (attach) {
                pane.setDocumentStore(store);
            }
            return new Fixture(pane, store, dependency, provider, failures, previousHandler);
        });
    }

    private static FeatureResult<StyleSpans<Collection<String>>> styles(FeatureRequestContext context, String label) {
        int length = ((TextDocumentSnapshot) context.documentSnapshot()).text().length();
        StyleSpans<Collection<String>> spans = StyleSpans.singleton(List.of(label), length);
        return new FeatureResult<>(context, spans);
    }

    private record Fixture(
        TestEditor editor,
        DefaultDocumentStore store,
        TextDocumentSnapshot dependency,
        ControlledProvider provider,
        BlockingQueue<Throwable> fxFailures,
        Thread.UncaughtExceptionHandler previousHandler
    ) implements AutoCloseable {
        private FeatureRequestContext nextRequest() throws InterruptedException {
            FeatureRequestContext context = provider.requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(context, "Editor did not dispatch a request-aware highlighting call");
            return context;
        }

        @Override
        public void close() throws Exception {
            onFx(() -> {
                try {
                    editor.highlightingDisabled = true;
                    editor.discardChangesOnClose();
                    editor.close();
                    editor.dispose();
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

    private static final class ControlledProvider implements SyntaxHighlightingProvider {
        private final BlockingQueue<FeatureRequestContext> requests = new LinkedBlockingQueue<>();
        private final Function<FeatureRequestContext, FeatureResult<StyleSpans<Collection<String>>>> computation;

        private ControlledProvider(
            Function<FeatureRequestContext, FeatureResult<StyleSpans<Collection<String>>>> computation
        ) {
            this.computation = computation;
        }

        @Override
        public StyleSpans<Collection<String>> compute(String text) {
            throw new AssertionError("Editor must use the request-aware highlighting overload");
        }

        @Override
        public FeatureResult<StyleSpans<Collection<String>>> compute(FeatureRequestContext context) {
            assertFalse(Platform.isFxApplicationThread());
            requests.add(context);
            // Deliberately ignore cancellation to exercise the editor's publication guard.
            return computation.apply(context);
        }
    }

    private static final class TestEditor extends CodeEditorPane {
        private boolean highlightingDisabled;

        private TestEditor(Project project, DocumentId id, Path file, CodeEditorConfig config) {
            super(project, id, file, config);
        }

        @Override
        protected boolean supportsHighlighting() {
            return !highlightingDisabled && super.supportsHighlighting();
        }

        @Override
        protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
            return false;
        }

        private void request() {
            invoke("requestHighlight");
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

        private List<String> highlightLabels() {
            return List.copyOf(lastHighlight.getStyleSpan(0).getStyle());
        }
    }
}
