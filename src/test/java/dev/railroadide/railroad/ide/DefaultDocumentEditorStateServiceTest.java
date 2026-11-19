package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.ui.IDECaretPositionPane;
import dev.railroadide.railroad.plugin.spi.editor.EditResult;
import dev.railroadide.railroad.plugin.spi.editor.EditorSubscription;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import dev.railroadide.railroad.plugin.spi.state.Cursor;
import dev.railroadide.railroad.plugin.spi.state.Selection;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultDocumentEditorStateServiceTest {
    @Test
    public void projectsCrLfUnicodeAndReversedSelectionUsingSnapshotCoordinates() {
        var service = new DefaultDocumentEditorStateService();
        var editor = new TestEditor("a\r\n\uD83D\uDE00x\rz");
        editor.selection = new TextSelection(8, 5);
        service.setActiveEditor(editor, "text");

        assertSame(editor, service.getActiveTextEditor().orElseThrow());
        assertEquals(List.of(new Cursor(1, 2)), service.getCursors());
        assertEquals(List.of(new Selection(new Cursor(1, 2), new Cursor(2, 1))), service.getSelections());
        assertEquals("text", service.getLanguageId());

        editor.selection = new TextSelection(2, 2);
        assertEquals(List.of(new Cursor(0, 2)), service.getCursors());
        assertTrue(service.getSelections().isEmpty());
        service.setActiveEditor(null, "image");
        assertTrue(service.getActiveTextEditor().isEmpty());
        assertTrue(service.getCursors().isEmpty());
        assertTrue(service.getSelections().isEmpty());
        assertEquals("", service.getLanguageId());
    }

    @Test
    public void observesInitialTargetSwitchesAbsenceAndReopenUntilDisposed() {
        var service = new DefaultDocumentEditorStateService();
        var first = new TestEditor("first");
        var second = new TestEditor("second");
        var changes = new ArrayList<Optional<TextEditorAccess>>();
        var subscription = service.observeActiveTextEditor(changes::add);

        service.setActiveEditor(first, "text");
        service.setActiveEditor(first, "text");
        service.setActiveEditor(second, "json");
        service.setActiveEditor(null, null);
        service.setActiveEditor(first, "text");
        assertEquals(List.of(Optional.empty(), Optional.of(first), Optional.of(second), Optional.empty(),
            Optional.of(first)), changes);

        subscription.close();
        subscription.close();
        service.setActiveEditor(second, "json");
        assertEquals(5, changes.size());
    }

    @Test
    public void disposalsAndReentrantActivationCannotDeliverSupersededTargets() {
        var service = new DefaultDocumentEditorStateService();
        var first = new TestEditor("first");
        var second = new TestEditor("second");
        var changes = new ArrayList<Optional<TextEditorAccess>>();
        var firstSubscription = service.observeActiveTextEditor(target -> {
            if (target.orElse(null) == first) {
                service.setActiveEditor(second, "text");
            }
        });
        var secondSubscription = service.observeActiveTextEditor(changes::add);
        service.setActiveEditor(first, "text");
        assertEquals(List.of(Optional.empty(), Optional.of(second)), changes);
        firstSubscription.close();
        secondSubscription.close();

        var disposedChanges = new ArrayList<Optional<TextEditorAccess>>();
        var pending = new EditorSubscription[1];
        var disposing = service.observeActiveTextEditor(target -> {
            if (target.orElse(null) == first) {
                pending[0].close();
            }
        });
        pending[0] = service.observeActiveTextEditor(disposedChanges::add);
        service.setActiveEditor(first, "text");
        assertEquals(List.of(Optional.of(second)), disposedChanges);
        disposing.close();
    }

    @Test
    public void initialListenerFailureDoesNotRetainRegistration() {
        var service = new DefaultDocumentEditorStateService();
        var calls = new ArrayList<Optional<TextEditorAccess>>();
        assertThrows(IllegalStateException.class, () -> service.observeActiveTextEditor(target -> {
            calls.add(target);
            throw new IllegalStateException("listener failure");
        }));
        service.setActiveEditor(new TestEditor("text"), "text");
        assertEquals(1, calls.size());
    }

    @Test
    public void caretPresentationReleasesSubscriptionsOnTargetSwitchAndSceneDetach() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var check = new FutureTask<>(() -> {
            var service = Services.DOCUMENT_EDITOR_STATE;
            var previous = service.getActiveTextEditor().orElse(null);
            String previousLanguage = service.getLanguageId();
            var first = new TestEditor("first");
            var second = new TestEditor("second");
            var pane = new IDECaretPositionPane();
            var root = new StackPane();
            new Scene(root);
            try {
                service.setActiveEditor(first, "text");
                root.getChildren().add(pane);
                assertEquals(1, first.selectionListeners.size());
                assertEquals(1, first.documentListeners.size());

                service.setActiveEditor(second, "text");
                assertTrue(first.selectionListeners.isEmpty());
                assertTrue(first.documentListeners.isEmpty());
                assertEquals(1, second.selectionListeners.size());

                root.getChildren().clear();
                assertTrue(second.selectionListeners.isEmpty());
                assertTrue(second.documentListeners.isEmpty());
                service.setActiveEditor(first, "text");
                assertTrue(first.selectionListeners.isEmpty());
                root.getChildren().add(pane);
                assertEquals(1, first.selectionListeners.size());
                assertEquals(1, first.documentListeners.size());

                service.setActiveEditor(null, null);
                assertTrue(first.selectionListeners.isEmpty());
                assertTrue(first.documentListeners.isEmpty());
                assertEquals("", ((Text) pane.getChildren().getFirst()).getText());
                assertEquals(0, first.reveals + second.reveals);
            } finally {
                root.getChildren().clear();
                service.setActiveEditor(previous, previousLanguage);
            }
            return null;
        });
        Platform.runLater(check);
        check.get(10, TimeUnit.SECONDS);
    }

    private static final class TestEditor implements TextEditorAccess {
        private final TextDocumentSnapshot snapshot;
        private TextSelection selection = new TextSelection(0, 0);
        private final List<Consumer<TextSelection>> selectionListeners = new ArrayList<>();
        private final List<Consumer<TextDocumentSnapshot>> documentListeners = new ArrayList<>();
        private int reveals;

        private TestEditor(String text) {
            var id = DocumentId.create();
            snapshot = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), new DocumentVersion(0), "text", text,
                StandardCharsets.UTF_8);
        }

        @Override
        public TextDocumentSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public TextSelection selection() {
            return selection;
        }

        @Override
        public EditResult selectAndReveal(TextDocumentSnapshot expected, TextSelection selection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEditable() {
            return false;
        }

        @Override
        public EditResult replace(TextDocumentSnapshot expected, List<TextReplacement> replacements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EditorSubscription observe(Consumer<TextDocumentSnapshot> listener) {
            documentListeners.add(listener);
            listener.accept(snapshot);
            return () -> documentListeners.remove(listener);
        }

        @Override
        public EditorSubscription observeSelection(Consumer<TextSelection> listener) {
            selectionListeners.add(listener);
            listener.accept(selection);
            return () -> selectionListeners.remove(listener);
        }

        @Override
        public void reveal() {
            reveals++;
        }
    }
}
