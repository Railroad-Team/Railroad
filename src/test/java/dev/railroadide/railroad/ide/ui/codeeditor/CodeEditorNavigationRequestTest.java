package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSstServices;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorNavigationRequestTest {
    private static final String SOURCE = "class Example { int value; int read() { return value; } }";
    private static final int REFERENCE = SOURCE.lastIndexOf("value");

    @TempDir
    public Path directory;

    @BeforeAll
    public static void initializeFx() throws Exception {
        startFx();
        onFx(() -> {
            Platform.setImplicitExit(false);
            Keybinds.initialize();
            return null;
        });
    }

    @Test
    public void shortcutClickNavigatesFromTheClickedCharacter() throws Exception {
        try (Fixture fixture = open((context, offset) -> target(context, 0))) {
            onFx(() -> {
                fixture.editor.moveTo(0);
                fixture.editor.layout();
                Bounds bounds = fixture.editor.getCharacterBoundsOnScreen(REFERENCE, REFERENCE + 1).orElseThrow();
                double screenX = bounds.getMinX() + bounds.getWidth() / 2;
                double screenY = bounds.getMinY() + bounds.getHeight() / 2;
                Point2D local = fixture.editor.screenToLocal(screenX, screenY);
                fixture.editor.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, local.getX(), local.getY(),
                    screenX, screenY, MouseButton.PRIMARY, 1, false, true, false, true, false, false, false, false,
                    false, true, null));
                return null;
            });
            fixture.nextRequest();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(0, fixture.editor.getCaretPosition());
                return null;
            });
        }
    }

    @Test
    public void plainClickDoesNotRequestNavigation() throws Exception {
        try (Fixture fixture = open((context, offset) -> target(context, 0))) {
            onFx(() -> {
                fixture.editor.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, false, false, true, null));
                return null;
            });
            awaitRequests(fixture.editor.featureRequests);
            assertTrue(fixture.requests.isEmpty());
        }
    }

    @Test
    public void f12NavigatesAJavaReferenceToItsDeclaration() throws Exception {
        try (var services = new JavaSstServices(); Fixture fixture = open(services)) {
            WorkspaceSnapshot workspace = fixture.store.capture();
            var snapshot = (TextDocumentSnapshot) workspace.documents().values().iterator().next();
            var warmup = new FeatureRequestContext(workspace, snapshot, CancellationToken.none(), Deadline.none());
            var expected = (Location.TextLocation) services.definitions(warmup, REFERENCE).value().getFirst();
            assertEquals("value", SOURCE.substring(expected.range().start, expected.range().end).trim());
            assertTrue(expected.range().end < REFERENCE);
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(expected.range().start, fixture.editor.getCaretPosition());
                return null;
            });
        }
    }

    @Test
    public void modifiedF12DoesNotRequestNavigation() throws Exception {
        try (Fixture fixture = open((context, offset) -> target(context, 0))) {
            onFx(() -> {
                for (int modifier = 0; modifier < 4; modifier++) {
                    fixture.editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F12,
                        modifier == 0, modifier == 1, modifier == 2, modifier == 3));
                }
                return null;
            });
            awaitRequests(fixture.editor.featureRequests);
            assertTrue(fixture.requests.isEmpty());
        }
    }

    @TestFactory
    public Stream<DynamicTest> unusableResultsLeaveTheCaretUnchanged() {
        return Stream.of("empty", "unsupported", "foreign", "stale", "bounds", "coordinates", "binary", "context")
            .map(kind -> DynamicTest.dynamicTest(kind, () -> assertUnusableResult(kind)));
    }

    private void assertUnusableResult(String kind) throws Exception {
        try (Fixture fixture = open((context, offset) -> {
            var snapshot = (TextDocumentSnapshot) context.documentSnapshot();
            Location<?> location = switch (kind) {
                case "foreign" -> new Location.TextLocation(DocumentId.create(), snapshot.version(),
                    new TextRange(0, 1), 0, 0);
                case "stale" -> new Location.TextLocation(snapshot.id(), snapshot.version().next(),
                    new TextRange(0, 1), 0, 0);
                case "bounds" -> new Location.TextLocation(snapshot.id(), snapshot.version(),
                    new TextRange(0, SOURCE.length() + 1), 0, 0);
                case "coordinates" -> new Location.TextLocation(snapshot.id(), snapshot.version(),
                    new TextRange(0, 1), 99, 0);
                case "binary" -> new Location.BinaryLocation(snapshot.id(), snapshot.version(), new BinaryRange(0, 1));
                default -> Location.TextLocation.from(snapshot, 0, 1);
            };
            if (kind.equals("context")) {
                var other = new FeatureRequestContext(context.workspaceSnapshot(), snapshot,
                    context.cancellationToken(), context.deadline());
                assertEquals(context, other);
                assertNotSame(context, other);
                return new FeatureResult<>(other, List.of(location));
            }
            List<Location<?>> locations = switch (kind) {
                case "empty" -> List.of();
                case "unsupported" -> null;
                default -> List.of(location);
            };
            return new FeatureResult<>(context, locations);
        })) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(REFERENCE, fixture.editor.getCaretPosition());
                return null;
            });
        }
    }

    @Test
    public void caretMovementRejectsAResultAlreadyQueuedForPublicationEvenAfterMovingBack() throws Exception {
        var release = new CountDownLatch(1);
        try (Fixture fixture = open((context, offset) -> {
            await(release);
            return target(context, 0);
        })) {
            try {
                Invocation request = fixture.request();
                try (FxBlocker blocker = FxBlocker.start(() -> {
                    fixture.editor.moveTo(REFERENCE + 1);
                    fixture.editor.moveTo(REFERENCE);
                })) {
                    release.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(request.context.cancellationToken().isCancellationRequested());
                    assertEquals(REFERENCE, fixture.editor.getCaretPosition());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @TestFactory
    public Stream<DynamicTest> lifecycleChangesRejectQueuedNavigation() {
        return Stream.of("edit", "configuration", "store", "close", "unload")
            .map(change -> DynamicTest.dynamicTest(change, () -> assertLifecycleChange(change)));
    }

    private void assertLifecycleChange(String change) throws Exception {
        var release = new CountDownLatch(1);
        var lifetime = new CancellationTokenSource();
        try (Fixture fixture = open((context, offset) -> {
            await(release);
            return target(context, 0);
        }, lifetime.token())) {
            try {
                Invocation request = fixture.request();
                try (FxBlocker blocker = FxBlocker.start(() -> {
                    switch (change) {
                        case "edit" -> {
                            fixture.editor.replaceText(0, 1, "C");
                            fixture.editor.moveTo(REFERENCE);
                        }
                        case "configuration" -> fixture.store.publishConfiguration(
                            new WorkspaceConfigurationSnapshot(1, Set.of("changed")));
                        case "store" -> {
                            WorkspaceSnapshot captured = request.context.workspaceSnapshot();
                            var replacement = new DefaultDocumentStore(captured.projectId(), captured.configuration());
                            captured.documents().values().forEach(replacement::publishBacking);
                            fixture.editor.setDocumentStore(replacement);
                        }
                        case "close" -> {
                            fixture.editor.discardChangesOnClose();
                            fixture.editor.close();
                            assertTrue(fixture.editor.isEditorClosed());
                        }
                        case "unload" -> lifetime.cancel();
                        default -> throw new AssertionError(change);
                    }
                })) {
                    release.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    blocker.releaseAndAwait();
                }
                onFx(() -> {
                    assertTrue(request.context.cancellationToken().isCancellationRequested());
                    assertEquals(REFERENCE, fixture.editor.getCaretPosition());
                    return null;
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void aNewRequestSupersedesAQueuedResultAtTheSameCaret() throws Exception {
        var releaseOld = new CountDownLatch(1);
        var releaseNew = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (Fixture fixture = open((context, offset) -> {
            int call = calls.incrementAndGet();
            await(call == 1 ? releaseOld : releaseNew);
            return target(context, call == 1 ? 0 : 6);
        })) {
            try {
                Invocation old = fixture.request();
                try (FxBlocker blocker = FxBlocker.start(() -> fireF12(fixture.editor))) {
                    releaseOld.countDown();
                    awaitRequests(fixture.editor.featureRequests);
                    blocker.releaseAndAwait();
                }
                fixture.nextRequest();
                onFx(() -> {
                    assertTrue(old.context.cancellationToken().isCancellationRequested());
                    assertEquals(REFERENCE, fixture.editor.getCaretPosition());
                    return null;
                });
                releaseNew.countDown();
                awaitRequests(fixture.editor.featureRequests);
                onFx(() -> {
                    assertEquals(6, fixture.editor.getCaretPosition());
                    return null;
                });
            } finally {
                releaseOld.countDown();
                releaseNew.countDown();
            }
        }
    }

    @Test
    public void providerFailureDoesNotPreventTheNextRequest() throws Exception {
        var calls = new AtomicInteger();
        try (Fixture fixture = open((context, offset) -> {
            if (calls.incrementAndGet() == 1)
                throw new IllegalStateException("Provider failed");
            return target(context, 0);
        })) {
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            fixture.request();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(0, fixture.editor.getCaretPosition());
                return null;
            });
        }
    }

    @Test
    public void rightClickMovesTheCaretToTheSymbolAndItsMenuNavigates() throws Exception {
        try (Fixture fixture = open((context, offset) -> target(context, 0))) {
            onFx(() -> {
                fixture.editor.moveTo(0);
                fireContextMenu(fixture.editor, REFERENCE + 1);
                assertEquals(REFERENCE + 1, fixture.editor.getCaretPosition());
                ContextMenu menu = fixture.editor.activeContextMenu();
                assertNotNull(menu);
                assertTrue(menu.isShowing());
                assertEquals(List.of("railroad:go_to_definition", "railroad:go_to_implementation",
                    "railroad:find_usages", "railroad:rename", "railroad:edit_cut", "railroad:edit_copy",
                    "railroad:edit_paste"),
                    menu.getItems().stream()
                        .map(item -> item.getProperties().get("railroad:command-id"))
                        .filter(Objects::nonNull)
                        .toList());
                MenuItem definition = menu.getItems().getFirst();
                assertFalse(definition.isDisable());
                assertTrue(menu.getItems().stream()
                    .filter(item -> "railroad:rename".equals(item.getProperties().get("railroad:command-id")))
                    .allMatch(MenuItem::isDisable), "Editors without a rename provider cannot rename");
                assertTrue(menu.getItems().stream()
                    .filter(item -> "railroad:edit_copy".equals(item.getProperties().get("railroad:command-id")))
                    .allMatch(MenuItem::isDisable));
                fixture.editor.moveTo(REFERENCE);
                definition.fire();
                return null;
            });
            fixture.nextRequest();
            awaitRequests(fixture.editor.featureRequests);
            onFx(() -> {
                assertEquals(0, fixture.editor.getCaretPosition());
                return null;
            });
        }
    }

    @Test
    public void rightClickInsideASelectionKeepsTheSelection() throws Exception {
        try (Fixture fixture = open((context, offset) -> target(context, 0))) {
            onFx(() -> {
                fixture.editor.selectRange(REFERENCE, REFERENCE + 5);
                fireContextMenu(fixture.editor, REFERENCE + 2);
                assertEquals(REFERENCE, fixture.editor.getSelection().getStart());
                assertEquals(REFERENCE + 5, fixture.editor.getSelection().getEnd());
                assertTrue(fixture.editor.activeContextMenu().getItems().stream()
                    .filter(item -> "railroad:edit_copy".equals(item.getProperties().get("railroad:command-id")))
                    .noneMatch(MenuItem::isDisable));
                fixture.editor.discardChangesOnClose();
                fixture.editor.close();
                assertNull(fixture.editor.activeContextMenu());
                return null;
            });
        }
    }

    private static void fireContextMenu(CodeEditorPane editor, int offset) {
        editor.layout();
        Bounds bounds = editor.getCharacterBoundsOnScreen(offset, offset + 1).orElseThrow();
        double screenX = bounds.getMinX() + bounds.getWidth() / 2;
        double screenY = bounds.getMinY() + bounds.getHeight() / 2;
        Point2D local = editor.screenToLocal(screenX, screenY);
        editor.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, local.getX(), local.getY(),
            screenX, screenY, false, null));
    }

    private Fixture open(NavigationProvider provider) throws Exception {
        return open(provider, CancellationToken.none());
    }

    private Fixture open(NavigationProvider provider, CancellationToken lifetime) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, SOURCE);
        DocumentId id = DocumentId.create();
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var requests = new LinkedBlockingQueue<Invocation>();
        Fixture fixture = onFx(() -> {
            var failures = new LinkedBlockingQueue<Throwable>();
            Thread.UncaughtExceptionHandler previous = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((_, failure) -> failures.add(failure));
            Project project = new RailroadProject(directory, "Navigation request", new WritableImage(1, 1));
            NavigationProvider controlled = (context, offset) -> {
                assertFalse(Platform.isFxApplicationThread());
                assertEquals(REFERENCE, offset);
                requests.add(new Invocation(context));
                return provider.definitions(context, offset);
            };
            var pane = new TestEditor(project, id, file,
                new CodeEditorConfig("java", null, null, null, controlled, null, lifetime));
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            var stage = new Stage();
            stage.setTitle("Railroad navigation test");
            stage.setScene(new Scene(pane, 480, 240));
            stage.show();
            pane.moveTo(REFERENCE);
            pane.requestFocus();
            return new Fixture(pane, stage, store, requests, failures, previous);
        });
        onFx(() -> null);
        return fixture;
    }

    private static FeatureResult<List<Location<?>>> target(FeatureRequestContext context, int start) {
        var snapshot = (TextDocumentSnapshot) context.documentSnapshot();
        return new FeatureResult<>(context, List.of(Location.TextLocation.from(snapshot, start, start + 1)));
    }

    private static void fireF12(CodeEditorPane editor) {
        editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F12, false, false, false, false));
    }

    private record Invocation(FeatureRequestContext context) {
    }

    private record Fixture(
        TestEditor editor,
        Stage stage,
        DefaultDocumentStore store,
        BlockingQueue<Invocation> requests,
        BlockingQueue<Throwable> fxFailures,
        Thread.UncaughtExceptionHandler previousHandler
    ) implements AutoCloseable {
        private Invocation request() throws Exception {
            onFx(() -> {
                fireF12(editor);
                return null;
            });
            return nextRequest();
        }

        private Invocation nextRequest() throws InterruptedException {
            Invocation invocation = requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(invocation, "F12 did not dispatch navigation");
            return invocation;
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
            awaitRequests(editor.featureRequests);
            if (!fxFailures.isEmpty())
                throw new AssertionError("Uncaught JavaFX navigation failure", fxFailures.peek());
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
    }
}
