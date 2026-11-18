package dev.railroadide.railroad.ide.ui.codeaction;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticOrigin;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.edit.WorkspaceEditService;
import dev.railroadide.railroad.ide.edit.WorkspaceEditorHost;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditProblem;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.sst.provider.api.CodeAction;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionProvider;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionRequest;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.ui.codeaction.CodeActionController.Discovery;
import dev.railroadide.railroad.ide.ui.codeaction.CodeActionController.Resolution;
import dev.railroadide.railroad.ide.ui.codeaction.CodeActionController.Status;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class CodeActionControllerTest {
    @TempDir
    public Path root;
    private DocumentStore store;
    private DocumentIdentityRegistry identities;
    private WorkspaceEditService service;
    private TestProvider provider;
    private CancellationTokenSource lifetime;
    private CodeActionController controller;
    private boolean serviceAvailable = true;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            LanguageSupports.initialize();
            return null;
        });
    }

    @BeforeEach
    public void createController() {
        store = new DefaultDocumentStore("code-actions", new WorkspaceConfigurationSnapshot(0, Set.of()));
        identities = new DocumentIdentityRegistry();
        service = new WorkspaceEditService(root, store, identities, new NoEditors());
        provider = new TestProvider();
        lifetime = new CancellationTokenSource();
        controller = new CodeActionController(provider, lifetime.token(), () -> store,
            () -> serviceAvailable ? Optional.of(service) : Optional.empty());
    }

    @AfterEach
    public void close() throws Exception {
        onFx(() -> {
            controller.close();
            service.close();
            return null;
        });
    }

    @Test
    public void resolvesDeferredActionsOnlyWhenSelectedAndAppliesThemAsOneUndoableStep() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha beta");
        provider.actions = (context, request) -> List.of(CodeAction.deferred("Capitalise", "first"));
        provider.resolver = (context, action) -> action.withEdit(WorkspaceEdit.builder(action.title())
            .replace((TextDocumentSnapshot) context.documentSnapshot(), replacement(0, 5, "ALPHA"))
            .build());

        Discovery discovery = discover(document, CodeActionRequest.at(0));
        assertEquals(1, discovery.actions().size());
        assertTrue(discovery.hasAvailableActions());
        assertEquals(0, provider.resolutions.get(), "Discovery must not resolve actions");

        Resolution resolution = resolve(discovery, discovery.actions().getFirst());
        assertEquals(Status.READY, resolution.status(), resolution.message());
        assertEquals(1, provider.resolutions.get());
        assertFalse(resolution.affectsOtherDocuments());
        assertEquals("ALPHA beta", resolution.preview().documents().getFirst().after());
        assertEquals("alpha beta", Files.readString(root.resolve("A.txt")), "Previews must not mutate");

        WorkspaceEditResult result = onFx(() -> controller.apply(resolution));
        assertTrue(result.applied(), result.problems()::toString);
        assertEquals("ALPHA beta", Files.readString(root.resolve("A.txt")));
        assertTrue(onFx(() -> service.undo()).applied());
        assertEquals("alpha beta", Files.readString(root.resolve("A.txt")));
        assertTrue(onFx(() -> service.redo()).applied());
        assertEquals("ALPHA beta", Files.readString(root.resolve("A.txt")));
    }

    @Test
    public void staleDiagnosticsAreNotPassedAndActionsForOtherRevisionsAreDropped() throws Exception {
        TextDocumentSnapshot old = disk("A.txt", "alpha beta");
        TextDocumentSnapshot current = republish(old, "alpha gamma");
        var staleDiagnostic = diagnostic(old, 0, 5);
        var currentDiagnostic = diagnostic(current, 0, 5);
        var seen = new AtomicReference<List<TextEditorDiagnostic>>();
        provider.actions = (context, request) -> {
            seen.set(request.diagnostics());
            return List.of(
                CodeAction.resolved("Old edit", WorkspaceEdit.builder("Old edit")
                    .replace(old, replacement(0, 1, "A")).build()),
                CodeAction.deferred("Old diagnostic", "x").withDiagnostics(List.of(staleDiagnostic)),
                CodeAction.deferred("Current", "y").withDiagnostics(List.of(currentDiagnostic)),
                CodeAction.unavailable("Preferred but unavailable", "Ambiguous").asPreferred());
        };

        Discovery discovery = discover(current, new CodeActionRequest(new TextRange(0, 0),
            List.of(staleDiagnostic, currentDiagnostic)));

        assertEquals(List.of(currentDiagnostic), seen.get());
        assertEquals(List.of("Preferred but unavailable", "Current"),
            discovery.actions().stream().map(CodeAction::title).toList());
        assertTrue(discovery.hasAvailableActions());
    }

    @Test
    public void refusesToResolveOrApplyOnceTheDocumentChanged() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha beta");
        provider.actions = (context, request) -> List.of(
            CodeAction.resolved("Edit", WorkspaceEdit.builder("Edit").replace(document, replacement(0, 5, "ALPHA"))
                .build()),
            CodeAction.deferred("Deferred", "x"));
        Discovery discovery = discover(document, CodeActionRequest.at(0));
        Resolution ready = resolve(discovery, discovery.actions().getFirst());
        assertEquals(Status.READY, ready.status());

        republish(document, "alpha beta!");

        assertEquals(Status.STALE, resolve(discovery, discovery.actions().get(1)).status());
        assertEquals(0, provider.resolutions.get(), "Stale actions must not be resolved");
        WorkspaceEditResult result = onFx(() -> controller.apply(ready));
        assertFalse(result.applied());
        assertEquals(WorkspaceEditProblem.Kind.STALE, result.problems().getFirst().kind());
        assertEquals("alpha beta!", Files.readString(root.resolve("A.txt")));
    }

    @Test
    public void reportsUnavailableUnsupportedAndFailedResolutionsExplicitly() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha");
        provider.actions = (context, request) -> List.of(
            CodeAction.unavailable("Ambiguous", "Two candidates"),
            CodeAction.deferred("No edit", "none"),
            CodeAction.deferred("Throws unsupported", "unsupported"),
            CodeAction.deferred("Gone", "gone"),
            CodeAction.deferred("Broken", "broken"),
            CodeAction.deferred("Overlapping", "overlap"));
        provider.resolver = (context, action) -> switch (Objects.requireNonNull(action.data())) {
            case "none" -> action;
            case "unsupported" -> throw new UnsupportedOperationException();
            case "gone" -> action.asUnavailable("Already fixed");
            case "broken" -> throw new IllegalStateException("Injected provider failure");
            default -> action.withEdit(WorkspaceEdit.builder("Overlapping")
                .replace(document, replacement(0, 3, "x"), replacement(2, 4, "y")).build());
        };
        Discovery discovery = discover(document, CodeActionRequest.at(0));

        Resolution ambiguous = resolve(discovery, discovery.actions().get(0));
        assertEquals(Status.UNAVAILABLE, ambiguous.status());
        assertEquals("Two candidates", ambiguous.message());
        assertEquals(0, provider.resolutions.get());
        assertEquals(Status.UNSUPPORTED, resolve(discovery, discovery.actions().get(1)).status());
        assertEquals(Status.UNSUPPORTED, resolve(discovery, discovery.actions().get(2)).status());
        Resolution gone = resolve(discovery, discovery.actions().get(3));
        assertEquals(Status.UNAVAILABLE, gone.status());
        assertEquals("Already fixed", gone.message());
        assertEquals(Status.FAILED, resolve(discovery, discovery.actions().get(4)).status());
        Resolution overlapping = resolve(discovery, discovery.actions().get(5));
        assertEquals(Status.FAILED, overlapping.status());
        assertFalse(overlapping.message().isBlank());
        assertThrows(IllegalArgumentException.class, () -> controller.apply(overlapping));
    }

    @Test
    public void rejectsResolvedEditsForAnotherRevisionAndMissingProjects() throws Exception {
        TextDocumentSnapshot old = disk("A.txt", "alpha");
        TextDocumentSnapshot current = republish(old, "alpha!");
        provider.actions = (context, request) -> List.of(CodeAction.deferred("Old", "old"),
            CodeAction.deferred("Current", "current"));
        provider.resolver = (context, action) -> action.withEdit(WorkspaceEdit.builder("Edit")
            .replace("old".equals(action.data()) ? old : current, replacement(0, 1, "A")).build());
        Discovery discovery = discover(current, CodeActionRequest.at(0));

        assertEquals(Status.FAILED, resolve(discovery, discovery.actions().getFirst()).status());
        Resolution ready = resolve(discovery, discovery.actions().get(1));
        assertEquals(Status.READY, ready.status());
        serviceAvailable = false;
        assertEquals(Status.FAILED, resolve(discovery, discovery.actions().get(1)).status());
        assertFalse(onFx(() -> controller.apply(ready)).applied());
        assertEquals("alpha!", Files.readString(root.resolve("A.txt")));
    }

    @Test
    public void flagsEditsReachingOtherDocumentsForConfirmation() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha");
        TextDocumentSnapshot other = disk("B.txt", "beta");
        provider.actions = (context, request) -> List.of(CodeAction.resolved("Both", WorkspaceEdit.builder("Both")
            .replace(document, replacement(0, 1, "A"))
            .replace(other, replacement(0, 1, "B"))
            .build()));
        Discovery discovery = discover(document, CodeActionRequest.at(0));

        Resolution resolution = resolve(discovery, discovery.actions().getFirst());
        assertEquals(Status.READY, resolution.status());
        assertTrue(resolution.affectsOtherDocuments());
    }

    @Test
    public void supersededClosedAndUnloadedRequestsAreCancelled() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha");
        provider.actions = (context, request) -> List.of(CodeAction.deferred("Slow", "slow"),
            CodeAction.deferred("Fast", "fast"));
        var started = new CountDownLatch(1);
        var observedCancellation = new CountDownLatch(1);
        provider.resolver = (context, action) -> {
            if ("slow".equals(action.data())) {
                started.countDown();
                awaitCancellation(context);
                observedCancellation.countDown();
                context.checkActive();
            }
            return action.withEdit(WorkspaceEdit.builder("Edit").replace(document, replacement(0, 1, "A")).build());
        };
        Discovery discovery = discover(document, CodeActionRequest.at(0));

        CompletableFuture<Resolution> slow = onFx(() -> controller.resolve(discovery, discovery.actions().get(0)));
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Resolution fast = resolve(discovery, discovery.actions().get(1));
        assertEquals(Status.READY, fast.status());
        assertTrue(observedCancellation.await(10, TimeUnit.SECONDS));
        assertCancelled(slow);

        var blocking = new CountDownLatch(1);
        provider.actions = (context, request) -> {
            blocking.countDown();
            awaitCancellation(context);
            context.checkActive();
            return List.of();
        };
        CompletableFuture<Discovery> pending = onFx(() -> controller.discover(store.capture(), document,
            CodeActionRequest.at(0), RequestPriority.NAVIGATION));
        assertTrue(blocking.await(10, TimeUnit.SECONDS));
        lifetime.cancel();
        assertCancelled(pending);
        assertCancelled(onFx(() -> controller.discover(store.capture(), document, CodeActionRequest.at(0),
            RequestPriority.NAVIGATION)));

        onFx(() -> {
            controller.close();
            return null;
        });
        assertTrue(controller.isClosed());
        assertCancelled(onFx(() -> controller.resolve(discovery, discovery.actions().get(1))));
    }

    @Test
    public void menuAppliesAChoiceMadeBeforeResolutionFinishes() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha");
        var release = new CountDownLatch(1);
        provider.actions = (context, request) -> List.of(CodeAction.deferred("Capitalise", "x"));
        provider.resolver = (context, action) -> {
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                throw new CancellationException();
            }
            return action.withEdit(WorkspaceEdit.builder("Edit").replace(document, replacement(0, 5, "ALPHA"))
                .build());
        };
        Discovery discovery = discover(document, CodeActionRequest.at(0));
        var applied = new CountDownLatch(1);
        CodeActionMenu menu = onFx(() -> {
            var opened = new CodeActionMenu(controller, discovery, applied::countDown);
            opened.choose();
            return opened;
        });
        assertEquals("alpha", Files.readString(root.resolve("A.txt")));
        release.countDown();
        assertTrue(applied.await(10, TimeUnit.SECONDS));
        assertTrue(onFx(menu::isClosed));
        assertEquals("ALPHA", Files.readString(root.resolve("A.txt")));
    }

    @Test
    public void menuConfirmsEditsToOtherDocumentsAndExplainsFailedApplications() throws Exception {
        TextDocumentSnapshot document = disk("A.txt", "alpha");
        TextDocumentSnapshot other = disk("B.txt", "beta");
        provider.actions = (context, request) -> List.of(
            CodeAction.resolved("Both", WorkspaceEdit.builder("Both")
                .replace(document, replacement(0, 1, "A")).replace(other, replacement(0, 1, "B")).build()),
            CodeAction.resolved("Local", WorkspaceEdit.builder("Local")
                .replace(document, replacement(0, 1, "a!")).build()));
        Discovery discovery = discover(document, CodeActionRequest.at(0));
        var applied = new AtomicInteger();
        CodeActionMenu menu = onFx(() -> new CodeActionMenu(controller, discovery, applied::incrementAndGet));

        onFx(() -> {
            menu.choose();
            return null;
        });
        assertFalse(onFx(menu::isClosed));
        assertEquals(L18n.localize("railroad.code_actions.confirm", 2), onFx(menu::statusText));
        assertEquals("beta", Files.readString(root.resolve("B.txt")));
        onFx(() -> {
            menu.choose();
            return null;
        });
        assertTrue(onFx(menu::isClosed));
        assertEquals(1, applied.get());
        assertEquals("Alpha", Files.readString(root.resolve("A.txt")));
        assertEquals("Beta", Files.readString(root.resolve("B.txt")));

        TextDocumentSnapshot current = (TextDocumentSnapshot) store.current(document.id()).orElseThrow();
        provider.actions = (context, request) -> List.of(CodeAction.resolved("Local", WorkspaceEdit.builder("Local")
            .replace((TextDocumentSnapshot) context.documentSnapshot(), replacement(0, 1, "a!")).build()));
        Discovery later = discover(current, CodeActionRequest.at(0));
        CodeActionMenu stale = onFx(() -> new CodeActionMenu(controller, later, applied::incrementAndGet));
        republish(current, "changed");
        for (int attempt = 0; attempt < 2; attempt++) {
            onFx(() -> {
                stale.choose();
                return null;
            });
            assertFalse(onFx(stale::isClosed));
            assertTrue(onFx(stale::statusText).startsWith(
                L18n.localize("railroad.code_actions.apply_failed", "").strip()), onFx(stale::statusText));
        }
        assertEquals(1, applied.get());
        assertEquals("changed", Files.readString(root.resolve("A.txt")));
        onFx(() -> {
            stale.close();
            return null;
        });
    }

    private static void awaitCancellation(FeatureRequestContext context) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!context.cancellationToken().isCancellationRequested()) {
            if (System.nanoTime() > deadline)
                throw new IllegalStateException("The request was never cancelled");
            Thread.onSpinWait();
        }
    }

    private Discovery discover(TextDocumentSnapshot document, CodeActionRequest request) throws Exception {
        return onFx(() -> controller.discover(store.capture(), document, request, RequestPriority.NAVIGATION))
            .get(10, TimeUnit.SECONDS);
    }

    private Resolution resolve(Discovery discovery, CodeAction action) throws Exception {
        return onFx(() -> controller.resolve(discovery, action)).get(10, TimeUnit.SECONDS);
    }

    private static void assertCancelled(CompletableFuture<?> future) {
        var failure = assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
        Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(CancellationException.class, cause);
    }

    private TextDocumentSnapshot disk(String name, String text) throws Exception {
        Path path = root.resolve(name);
        Files.writeString(path, text);
        DocumentId id = identities.getOrCreate(path);
        var snapshot = new TextDocumentSnapshot(id, DocumentUri.fromPath(path), DocumentVersion.initial(),
            "plaintext", DocumentOrigin.original(DocumentUri.fromPath(path)), text, StandardCharsets.UTF_8);
        store.publishBacking(snapshot);
        return snapshot;
    }

    private TextDocumentSnapshot republish(TextDocumentSnapshot snapshot, String text) throws Exception {
        Files.writeString(snapshot.uri().filePath().orElseThrow(), text);
        var newer = new TextDocumentSnapshot(snapshot.id(), snapshot.uri(), store.latestVersion(snapshot.id())
            .orElseThrow().next(), snapshot.languageId(), snapshot.origin(), text, snapshot.encoding());
        store.publishBacking(newer);
        return newer;
    }

    private static TextEditorDiagnostic diagnostic(TextDocumentSnapshot snapshot, int start, int end) {
        return new TextEditorDiagnostic(Location.TextLocation.from(snapshot, start, end), DiagnosticSeverity.WARNING,
            "test", "message", "railroad:test", DiagnosticOrigin.INSPECTION, Set.of(), List.of(), null);
    }

    private static TextReplacement replacement(int start, int end, String text) {
        return new TextReplacement(new TextRange(start, end), text);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread())
            return action.call();
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static final class TestProvider implements CodeActionProvider {
        private final AtomicInteger resolutions = new AtomicInteger();
        private volatile BiFunction<FeatureRequestContext, CodeActionRequest, List<CodeAction>> actions = (
            context,
            request) -> List.of();
        private volatile BiFunction<FeatureRequestContext, CodeAction, CodeAction> resolver;

        @Override
        public FeatureResult<List<CodeAction>> actions(FeatureRequestContext context, CodeActionRequest request) {
            return new FeatureResult<>(context, actions.apply(context, request));
        }

        @Override
        public FeatureResult<CodeAction> resolve(FeatureRequestContext context, CodeAction action) {
            resolutions.incrementAndGet();
            return new FeatureResult<>(context, resolver == null ? action : resolver.apply(context, action));
        }
    }

    private static final class NoEditors implements WorkspaceEditorHost {
        @Override
        public Optional<TextEditorPane> openEditor(DocumentId id) {
            return Optional.empty();
        }

        @Override
        public boolean isOpen(DocumentId id) {
            return false;
        }

        @Override
        public void relocate(DocumentIdentity identity, Path newPath) {
        }
    }
}
