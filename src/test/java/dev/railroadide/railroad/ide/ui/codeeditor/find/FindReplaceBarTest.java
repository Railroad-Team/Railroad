package dev.railroadide.railroad.ide.ui.codeeditor.find;

import dev.railroadide.railroad.ide.sst.document.api.DefaultDocumentStore;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.IndexRange;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactfx.Subscription;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;
import javafx.geometry.Bounds;

import static org.junit.jupiter.api.Assertions.*;

public class FindReplaceBarTest {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    @TempDir
    public Path directory;
    private final List<TextEditorPane> editors = new ArrayList<>();
    private Stage stage;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }

        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @AfterEach
    public void closeEditors() throws Exception {
        onFx(() -> {
            if (stage != null) {
                stage.close();
            }

            for (TextEditorPane editor : editors) {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            }

            return null;
        });
    }

    @Test
    public void openSeedsTheSearchWithTheSelectionAndSelectsItsMatch() throws Exception {
        FindReplaceBar bar = show("alpha beta alpha beta");
        onFx(() -> {
            editor().selectRange(11, 16);
            bar.open(false);

            assertTrue(bar.isOpen());
            assertFalse(bar.isReplaceVisible());
            assertEquals("alpha", bar.searchField().getText());
            assertEquals(List.of(range(0, 5), range(11, 16)), bar.matches());
            assertEquals(1, bar.currentIndex());
            assertEquals(new IndexRange(11, 16), editor().getSelection());
            return null;
        });
    }

    @Test
    public void typingSelectsTheFirstMatchFromTheCaretAndNavigationWraps() throws Exception {
        FindReplaceBar bar = show("alpha beta alpha beta");
        onFx(() -> {
            editor().moveTo(7);
            bar.open(false);
            bar.searchField().setText("beta");

            assertEquals(new IndexRange(17, 21), editor().getSelection());
            bar.findNext();
            assertEquals(new IndexRange(6, 10), editor().getSelection());
            assertEquals(0, bar.currentIndex());
            bar.findPrevious();
            assertEquals(new IndexRange(17, 21), editor().getSelection());
            bar.findPrevious();
            assertEquals(new IndexRange(6, 10), editor().getSelection());
            return null;
        });
    }

    @Test
    public void optionsChangeTheMatchesAndInvalidRegexIsFlagged() throws Exception {
        FindReplaceBar bar = show("Foo foo food f.o");
        onFx(() -> {
            bar.open(false);
            bar.searchField().setText("foo");
            assertEquals(3, bar.matches().size());

            bar.matchCaseToggle().setSelected(true);
            assertEquals(List.of(range(4, 7), range(8, 11)), bar.matches());
            bar.wholeWordsToggle().setSelected(true);
            assertEquals(List.of(range(4, 7)), bar.matches());

            bar.wholeWordsToggle().setSelected(false);
            bar.searchField().setText("f.o");
            assertEquals(List.of(range(13, 16)), bar.matches());
            bar.regexToggle().setSelected(true);
            assertEquals(List.of(range(4, 7), range(8, 11), range(13, 16)), bar.matches());

            bar.searchField().setText("f(");
            assertTrue(bar.matches().isEmpty());
            assertTrue(bar.searchField().getPseudoClassStates().contains(ERROR));
            assertFalse(bar.statusText().isEmpty());
            bar.searchField().setText("f");
            assertFalse(bar.searchField().getPseudoClassStates().contains(ERROR));
            return null;
        });
    }

    @Test
    public void selectionSeedIsEscapedInRegexMode() throws Exception {
        FindReplaceBar bar = show("call(a.b) call(a.b)");
        onFx(() -> {
            bar.open(false);
            bar.regexToggle().setSelected(true);
            editor().selectRange(10, 19);
            bar.open(false);

            assertEquals("call\\(a\\.b\\)", bar.searchField().getText());
            assertEquals(List.of(range(0, 9), range(10, 19)), bar.matches());
            assertEquals(1, bar.currentIndex());
            return null;
        });
    }

    @Test
    public void replaceCurrentReplacesTheSelectedMatchAndSelectsTheNext() throws Exception {
        FindReplaceBar bar = show("a1 a2 a3");
        onFx(() -> {
            bar.open(true);
            assertTrue(bar.isReplaceVisible());
            bar.regexToggle().setSelected(true);
            bar.searchField().setText("a(\\d)");
            bar.replaceField().setText("b$1$1");
            assertEquals(new IndexRange(0, 2), editor().getSelection());

            bar.replaceCurrent();
            assertEquals("b11 a2 a3", editor().getText());
            assertEquals(new IndexRange(4, 6), editor().getSelection());
            assertEquals(List.of(range(4, 6), range(7, 9)), bar.matches());

            editor().moveTo(0);
            bar.replaceCurrent();
            assertEquals("b11 a2 a3", editor().getText(), "Without a selected match, replace only finds the next");
            assertEquals(new IndexRange(4, 6), editor().getSelection());
            return null;
        });
    }

    @Test
    public void replaceAllIsASingleUndoableEdit() throws Exception {
        FindReplaceBar bar = show("x y x y x");
        onFx(() -> {
            bar.open(true);
            bar.searchField().setText("x");
            bar.replaceField().setText("zz");
            bar.replaceAll();

            assertEquals("zz y zz y zz", editor().getText());
            assertTrue(bar.matches().isEmpty());
            editor().undo();
            assertEquals("x y x y x", editor().getText());
            return null;
        });
    }

    @Test
    public void invalidReplacementLeavesTheDocumentUnchanged() throws Exception {
        FindReplaceBar bar = show("a a");
        onFx(() -> {
            bar.open(true);
            bar.regexToggle().setSelected(true);
            bar.searchField().setText("(a)");
            bar.replaceField().setText("$2");
            bar.replaceAll();

            assertEquals("a a", editor().getText());
            assertFalse(bar.statusText().isEmpty());
            return null;
        });
    }

    @Test
    public void readOnlyEditorsAreNotReplaced() throws Exception {
        FindReplaceBar bar = show("a a");
        onFx(() -> {
            editor().setEditable(false);
            bar.open(true);
            bar.searchField().setText("a");
            bar.replaceField().setText("b");
            bar.replaceAll();
            bar.replaceCurrent();

            assertEquals("a a", editor().getText());
            assertEquals(2, bar.matches().size());
            return null;
        });
    }

    @Test
    public void navigationUsesEditsMadeSinceTheLastSearch() throws Exception {
        FindReplaceBar bar = show("one two");
        onFx(() -> {
            bar.open(false);
            bar.searchField().setText("two");
            editor().insertText(0, "two ");
            editor().moveTo(0);
            bar.findNext();

            assertEquals(List.of(range(0, 3), range(8, 11)), bar.matches());
            assertEquals(new IndexRange(0, 3), editor().getSelection());
            return null;
        });
    }

    @Test
    public void matchesAreHighlightedWhileOpenAndClearedOnClose() throws Exception {
        FindReplaceBar bar = show("match other match\nmatch");
        onFx(() -> {
            editor().moveTo(0);
            bar.open(false);
            bar.searchField().setText("match");
            return null;
        });

        Thread.sleep(300);
        onFx(() -> {
            assertEquals(3, bar.highlighter().drawnSegmentCount());
            assertEquals(1, editor().lookupAll("." + FindMatchHighlighter.CURRENT_MATCH_STYLE_CLASS).size());
            assertEquals(2, editor().lookupAll("." + FindMatchHighlighter.MATCH_STYLE_CLASS).size());

            bar.close();
            assertFalse(bar.isOpen());
            assertNull(bar.highlighter());
            assertTrue(editor().lookupAll("." + FindMatchHighlighter.MATCH_STYLE_CLASS).isEmpty());
            assertTrue(editor().lookupAll("." + FindMatchHighlighter.CURRENT_MATCH_STYLE_CLASS).isEmpty());
            return null;
        });
    }

    @Test
    public void retargetingMovesAnOpenSearchToTheNewEditor() throws Exception {
        FindReplaceBar bar = show("first first");
        TextEditorPane second = onFx(() -> createEditor("other.txt", "first"));
        onFx(() -> {
            bar.open(false);
            bar.searchField().setText("first");
            assertEquals(2, bar.matches().size());

            bar.setEditor(second);
            assertTrue(bar.isOpen());
            assertEquals(List.of(range(0, 5)), bar.matches());

            bar.setEditor(null);
            assertFalse(bar.isOpen());
            return null;
        });
    }

    @Test
    public void barReservesSpaceOnlyWhileTheDocumentStartIsVisible() throws Exception {
        FindReplaceBar bar = show(numberedLines(200, -1));
        EditorFindHost host = (EditorFindHost) bar.getParent();
        onFx(() -> {
            bar.open(true);
            return null;
        });

        Thread.sleep(300);
        onFx(() -> {
            assertTrue(bar.getHeight() > 0);
            assertEquals(bar.prefWidth(-1), bar.getWidth(), 1, "The bar is only as wide as its controls");
            assertTrue(bar.getWidth() < host.getWidth());
            assertEquals(bar.getHeight(), editor().getLayoutY(), 1);
            assertEquals(host.getHeight() - bar.getHeight(), editor().getHeight(), 1);
            editor().showParagraphAtTop(100);
            return null;
        });

        Thread.sleep(300);
        onFx(() -> {
            assertEquals(0, host.reservedHeight(), 0.5);
            assertEquals(0, editor().getLayoutY(), 0.5);
            assertEquals(host.getHeight(), editor().getHeight(), 1);
            assertTrue(bar.isVisible());
            assertTrue(bar.getPseudoClassStates().contains(PseudoClass.getPseudoClass("overlaying")));

            editor().showParagraphAtTop(0);
            return null;
        });

        Thread.sleep(300);
        onFx(() -> {
            assertEquals(bar.getHeight(), editor().getLayoutY(), 1);
            bar.close();
            return null;
        });

        Thread.sleep(300);
        onFx(() -> {
            assertEquals(0, editor().getLayoutY(), 0.5);
            assertEquals(host.getHeight(), editor().getHeight(), 1);
            return null;
        });
    }

    @Test
    public void navigationScrollsMatchesOutFromUnderTheBar() throws Exception {
        FindReplaceBar bar = show(numberedLines(200, 100));
        onFx(() -> {
            bar.open(true);
            bar.searchField().setText("target");
            editor().moveTo(editor().getAbsolutePosition(100, 0));
            editor().showParagraphAtTop(100);
            return null;
        });

        Thread.sleep(300);
        onFx(() -> {
            bar.findNext();
            assertEquals(0, bar.currentIndex());
            Bounds barBounds = bar.localToScreen(bar.getLayoutBounds());
            TextRange match = bar.matches().getFirst();
            Bounds matchBounds = editor().getCharacterBoundsOnScreen(match.start, match.end).orElseThrow();
            assertTrue(matchBounds.getMinY() >= barBounds.getMaxY() - 0.5,
                "Match at " + matchBounds.getMinY() + " is hidden by the bar ending at " + barBounds.getMaxY());
            return null;
        });
    }

    private static String numberedLines(int count, int targetLine) {
        var text = new StringBuilder();
        for (int line = 0; line < count; line++) {
            text.append(line == targetLine ? "target line " : "line ").append(line).append('\n');
        }

        return text.toString();
    }

    private FindReplaceBar show(String text) throws Exception {
        return onFx(() -> {
            TextEditorPane editor = createEditor("example.txt", text);
            var host = new EditorFindHost(editor);
            host.setEditor(editor);
            stage = new Stage();
            stage.setScene(new Scene(host, 700, 300));
            stage.show();
            host.applyCss();
            host.layout();
            return host.bar();
        });
    }

    private TextEditorPane createEditor(String name, String text) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, text);
        var store = new DefaultDocumentStore("find-replace-test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var editor = new TextEditorPane(DocumentId.create(), file, "plaintext") {
            @Override
            protected void showExternalChangeDialog(String disk) {
            }
        };

        editors.add(editor);
        Field autosave = TextEditorPane.class.getDeclaredField("changeSubscription");
        autosave.setAccessible(true);
        ((Subscription) autosave.get(editor)).unsubscribe();
        store.publishBacking(editor.captureSnapshot());
        editor.setDocumentStore(store);
        return editor;
    }

    private TextEditorPane editor() {
        return editors.getFirst();
    }

    private static TextRange range(int start, int end) {
        return new TextRange(start, end);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
