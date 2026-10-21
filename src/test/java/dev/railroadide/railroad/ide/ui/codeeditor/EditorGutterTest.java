package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.debug.breakpoint.BreakpointService;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.shape.Circle;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class EditorGutterTest {
    @BeforeAll
    public static void startJavaFx() throws Exception {
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void emptyCellsHandleClicksAlongsideOtherMarkers() throws Exception {
        onFxThread(() -> {
            var editor = new CodeArea("first\nsecond");
            try {
                var gutter = new EditorGutter(editor);
                var clickedLine = new AtomicInteger();
                gutter.addColumn(new GutterColumn("diagnostics", 12, line -> new Text("warning " + line)));
                gutter.addColumn(new GutterColumn("breakpoints", 16, _ -> null, (line, event) -> {
                    clickedLine.set(line);
                    event.consume();
                }));

                var row = (GridPane) editor.getParagraphGraphicFactory().apply(1);
                assertEquals(3, row.getChildren().size());
                var diagnostic = (StackPane) row.getChildren().get(1);
                assertEquals("warning 2", ((Text) diagnostic.getChildren().getFirst()).getText());
                var emptyCell = (StackPane) row.getChildren().get(2);
                assertTrue(emptyCell.getChildren().isEmpty());
                assertTrue(emptyCell.isPickOnBounds());
                assertEquals(16, emptyCell.getMinWidth());
                Event.fireEvent(emptyCell, new MouseEvent(MouseEvent.MOUSE_CLICKED,
                    1, 1, 1, 1, MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, false, false, true, null));
                assertEquals(2, clickedLine.get());
            } finally {
                editor.dispose();
            }
        });
    }

    @Test
    public void replacingAndRemovingColumnsPreservesOtherMarkers() throws Exception {
        onFxThread(() -> {
            var editor = new CodeArea("first");
            try {
                var gutter = new EditorGutter(editor);
                gutter.addColumn(new GutterColumn("first", 12, _ -> new Text("old")));
                gutter.addColumn(new GutterColumn("second", 12, _ -> new Text("second")));
                gutter.addColumn(new GutterColumn("first", 12, _ -> new Text("replacement")));
                var row = (GridPane) editor.getParagraphGraphicFactory().apply(0);
                assertEquals(3, row.getChildren().size());
                assertEquals("replacement", markerText(row, 1));
                assertEquals("second", markerText(row, 2));

                gutter.removeColumn("first");
                row = (GridPane) editor.getParagraphGraphicFactory().apply(0);
                assertEquals(2, row.getChildren().size());
                assertEquals("second", markerText(row, 1));
            } finally {
                editor.dispose();
            }
        });
    }

    @Test
    public void refreshUpdatesAlreadyRenderedMarkers() throws Exception {
        onFxThread(() -> {
            var editor = new CodeArea("first\nsecond");
            try {
                var gutter = new EditorGutter(editor);
                var state = new AtomicReference<>("old");
                gutter.addColumn(new GutterColumn("marker", 24, _ -> new Text(state.get())));
                var root = new StackPane(editor);
                new Scene(root, 400, 200);
                root.resize(400, 200);
                root.applyCss();
                root.layout();
                assertVisibleMarkers(root, "old");

                state.set("new");
                gutter.refresh();
                root.applyCss();
                root.layout();
                assertVisibleMarkers(root, "new");
            } finally {
                editor.dispose();
            }
        });
    }

    private static String markerText(GridPane row, int column) {
        var cell = (StackPane) row.getChildren().get(column);
        return ((Text) cell.getChildren().getFirst()).getText();
    }

    @Test
    public void breakpointClicksSynchronizeViewsAndClosingUnsubscribes() throws Exception {
        onFxThread(() -> {
            var service = new BreakpointService();
            Path file = Path.of("Example.java");
            var first = new CodeArea("first\nsecond");
            var second = new CodeArea("first\nsecond");
            var firstGutter = new EditorGutter(first);
            var secondGutter = new EditorGutter(second);
            try (var firstBinding = new BreakpointGutterBinding(firstGutter, first, () -> file, service);
                var secondBinding = new BreakpointGutterBinding(secondGutter, second, () -> file, service)) {
                var previousFactory = second.getParagraphGraphicFactory();
                clickBreakpoint(first, 1, MouseButton.SECONDARY);
                assertFalse(service.has(file, 2));
                clickBreakpoint(first, 1, MouseButton.PRIMARY);
                assertTrue(service.has(file, 2));
                assertNotSame(previousFactory, second.getParagraphGraphicFactory());
                var row = (GridPane) second.getParagraphGraphicFactory().apply(1);
                var cell = (StackPane) row.getChildren().get(1);
                assertInstanceOf(Circle.class, cell.getChildren().getFirst());

                previousFactory = second.getParagraphGraphicFactory();
                service.add(Path.of("Unrelated.java"), 2);
                assertSame(previousFactory, second.getParagraphGraphicFactory());
                clickBreakpoint(second, 1, MouseButton.PRIMARY);
                assertFalse(service.has(file, 2));

                firstBinding.close();
                var closedFactory = first.getParagraphGraphicFactory();
                service.add(file, 2);
                assertSame(closedFactory, first.getParagraphGraphicFactory());
            } finally {
                first.dispose();
                second.dispose();
            }
        });
    }

    private static void clickBreakpoint(CodeArea editor, int paragraph, MouseButton button) {
        var row = (GridPane) editor.getParagraphGraphicFactory().apply(paragraph);
        var cell = (StackPane) row.getChildren().get(1);
        Event.fireEvent(cell, new MouseEvent(MouseEvent.MOUSE_CLICKED,
            1, 1, 1, 1, button, 1,
            false, false, false, false, false, false, false, false, false, true, null));
    }

    @Test
    public void breakpointFollowsEditsUndoRedoAndReloadWithoutMovingTwice() throws Exception {
        onFxThread(() -> {
            var service = new BreakpointService();
            Path file = Path.of("Example.java");
            String original = "first\ntarget();\nlast";
            var first = new CodeArea(original);
            var second = new CodeArea(original);
            var firstGutter = new EditorGutter(first);
            var secondGutter = new EditorGutter(second);
            try (var firstBinding = new BreakpointGutterBinding(firstGutter, first, () -> file, service);
                var secondBinding = new BreakpointGutterBinding(secondGutter, second, () -> file, service)) {
                var breakpoint = service.add(file, 2);
                // Identical inserted code must not confuse the position with the existing line.
                first.insertText(6, "target();\n");
                assertEquals(breakpoint.id(), service.get(file, 3).orElseThrow().id());
                var row = (GridPane) first.getParagraphGraphicFactory().apply(2);
                assertInstanceOf(Circle.class, ((StackPane) row.getChildren().get(1)).getChildren().getFirst());
                second.replaceText(first.getText());
                assertEquals(breakpoint.id(), service.get(file, 3).orElseThrow().id());

                first.undo();
                assertEquals(breakpoint, service.get(file, 2).orElseThrow());
                first.redo();
                assertEquals(breakpoint.id(), service.get(file, 3).orElseThrow().id());
                first.deleteText(6, 16);
                assertEquals(breakpoint, service.get(file, 2).orElseThrow());

                firstBinding.close();
                first.insertText(0, "closed\n");
                assertEquals(breakpoint, service.get(file, 2).orElseThrow());
            } finally {
                first.dispose();
                second.dispose();
            }
        });
    }

    @Test
    public void splittingIndentationMovesTheBreakpointWithCodeAndDeletingItsLineRemovesIt() throws Exception {
        onFxThread(() -> {
            var service = new BreakpointService();
            Path file = Path.of("Example.java");
            var editor = new CodeArea("first\n    target();\nlast");
            var gutter = new EditorGutter(editor);
            try (var binding = new BreakpointGutterBinding(gutter, editor, () -> file, service)) {
                var breakpoint = service.add(file, 2);
                editor.insertText(8, "\n");
                assertEquals(breakpoint.id(), service.get(file, 3).orElseThrow().id());
                editor.deleteText(9, 21);
                assertTrue(service.getForFile(file).isEmpty());
            } finally {
                editor.dispose();
            }
        });
    }

    private static void assertVisibleMarkers(StackPane root, String expected) {
        var cells = root.lookupAll(".editor-gutter-cell");
        assertFalse(cells.isEmpty());
        for (var node : cells) {
            var cell = (StackPane) node;
            assertEquals(expected, ((Text) cell.getChildren().getFirst()).getText());
        }
    }

    private static void onFxThread(Runnable action) throws Exception {
        var task = new FutureTask<Void>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
