package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class IDEActivityPaneTest {
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
    }

    @Test
    public void showsNewestActivityWithCountOfOthersAndHidesWhenIdle() throws Exception {
        var clock = new AtomicLong();
        var activities = new BackgroundActivities(clock::get);
        IDEActivityPane pane = onFx(() -> {
            var created = new IDEActivityPane(activities);
            new Scene(created);
            return created;
        });
        assertFalse(onFx(pane::isVisible));

        var importing = activities.start("railroad.activity.importing_gradle");
        var bytes = importing.begin("download.jar", ActivityProgress.Unit.BYTES, 0, 4096);
        bytes.accept(512);
        var indexing = activities.start("railroad.activity.indexing_libraries");
        assertTrue(onFx(() -> bar(pane).isIndeterminate()));
        List<String> shown = onFx(() -> labels(pane));
        assertTrue(onFx(pane::isVisible));
        assertEquals(List.of(L18n.localize("railroad.activity.indexing_libraries"),
            L18n.localize("railroad.activity.others", 1)), shown);

        var processed = indexing.count("files", ActivityProgress.Unit.COUNT, 4);
        processed.run();
        assertEquals(0.25, onFx(() -> bar(pane).getProgress()));
        assertTrue(onFx(() -> labels(pane).contains("1/4")));
        clock.set(TimeUnit.SECONDS.toNanos(2));
        bytes.accept(2048);
        indexing.close();
        String bytesAndEta = "%.1f/%.1f KiB".formatted(2.0, 4.0) + " · "
            + L18n.localize("railroad.activity.eta.seconds", 2L);
        assertEquals(List.of(L18n.localize("railroad.activity.importing_gradle"), "download.jar", bytesAndEta, ""),
            onFx(() -> labels(pane)));
        assertEquals(0.5, onFx(() -> bar(pane).getProgress()));
        importing.indeterminate("saving");
        assertTrue(onFx(() -> bar(pane).isIndeterminate()));
        assertEquals(List.of(L18n.localize("railroad.activity.importing_gradle"), "saving", ""),
            onFx(() -> labels(pane)));
        importing.close();
        assertFalse(onFx(pane::isVisible));
    }

    @Test
    public void statusBarKeepsActivityBesideCaretAtRightEdge() throws Exception {
        onFx(() -> {
            try (var _ = BackgroundActivities.shared().start("railroad.activity.indexing_libraries")) {
                var statusBar = new IDEStatusBarPane(new Pane(), new IDEActivityPane());
                var scene = new Scene(statusBar, 900, 32);
                try {
                    statusBar.applyCss();
                    statusBar.layout();
                    var activity = statusBar.lookup(".rr-activity-pane");
                    var caret = statusBar.lookup("IDECaretPositionPane");
                    var indicators = (HBox) activity.getParent();
                    assertTrue(activity.isManaged());
                    assertEquals(caret.getBoundsInParent().getMinX(),
                        activity.getBoundsInParent().getMaxX() + indicators.getSpacing(), 0.5);
                    assertEquals(indicators.getWidth(), caret.getBoundsInParent().getMaxX(), 0.5);
                    assertEquals(statusBar.getWidth() - 8, caret.localToScene(caret.getLayoutBounds()).getMaxX(), 0.5);
                } finally {
                    scene.setRoot(new Pane());
                }
            }
            return null;
        });
    }

    @Test
    public void clickingProgressFloatsTasksOverTheEditorUntilDismissed() throws Exception {
        var clock = new AtomicLong();
        var activities = new BackgroundActivities(clock::get);
        var first = activities.start("railroad.activity.indexing_libraries");
        var files = first.count("first library", ActivityProgress.Unit.COUNT, 8);
        files.run();
        var second = activities.start("railroad.activity.indexing_libraries");
        second.indeterminate("second library");
        var pane = onFx(() -> new IDEActivityPane(activities));
        var stage = onFx(() -> {
            Platform.setImplicitExit(false);
            var window = new Stage();
            var editor = new Pane();
            editor.setFocusTraversable(true);
            var workspace = new BorderPane(new StackPane(editor, pane.getTaskOverlay()));
            workspace.setBottom(new IDEStatusBarPane(new Pane(), pane));
            window.setScene(new Scene(workspace, 680, 400));
            window.show();
            return window;
        });
        try {
            VBox rows = onFx(() -> {
                var workspace = (BorderPane) stage.getScene().getRoot();
                var stack = (StackPane) workspace.getCenter();
                var editor = stack.getChildren().getFirst();
                var editorBounds = editor.localToScene(editor.getLayoutBounds());
                var statusBar = workspace.getBottom();
                var statusBounds = statusBar.localToScene(statusBar.getLayoutBounds());
                var bounds = bar(pane).localToScene(bar(pane).getBoundsInLocal());
                var screen = pane.getScene().getRoot().localToScreen(bounds.getCenterX(), bounds.getCenterY());
                for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
                    pane.fireEvent(new MouseEvent(type, bounds.getCenterX(), bounds.getCenterY(), screen.getX(),
                        screen.getY(), MouseButton.PRIMARY, 1, false, false, false, false,
                        type == MouseEvent.MOUSE_PRESSED, false, false, false, false, true,
                        new PickResult(pane, bounds.getCenterX(), bounds.getCenterY())));
                }
                workspace.applyCss();
                workspace.layout();
                var list = (ScrollPane) workspace.lookup(".rr-activity-list");
                var overlay = pane.getTaskOverlay();
                assertTrue(overlay.isVisible());
                assertSame(overlay, stack.getChildren().getLast());
                assertSame(stage.getScene(), list.getScene());
                var listBounds = list.localToScene(list.getLayoutBounds());
                assertEquals(editorBounds, editor.localToScene(editor.getLayoutBounds()));
                assertEquals(statusBounds, statusBar.localToScene(statusBar.getLayoutBounds()));
                assertTrue(editorBounds.contains(listBounds));
                assertTrue(listBounds.getHeight() > 0);
                assertEquals(statusBounds.getMinY() - 4, listBounds.getMaxY(), 0.5);
                assertEquals(pane.localToScene(pane.getLayoutBounds()).getMaxX(), listBounds.getMaxX(), 0.5);
                assertFalse(overlay.contains(1, 1));
                editor.requestFocus();
                assertSame(editor, stage.getScene().getFocusOwner());
                assertTrue(overlay.isVisible());
                var content = (VBox) list.getContent();
                assertEquals(2, content.getChildren().size());
                assertTaskRow(content, 0, "second library", "", -1);
                assertTaskRow(content, 1, "first library", "1/8", 0.125);
                return content;
            });

            clock.set(TimeUnit.SECONDS.toNanos(2));
            files.run();
            var third = activities.start("railroad.activity.git_fetch");
            third.indeterminate("fetching");
            second.close();
            onFx(() -> {
                assertTrue(pane.isSelected());
                assertEquals(2, rows.getChildren().size());
                assertTaskRow(rows, 0, "fetching", "", -1);
                assertTaskRow(rows, 1, "first library", "2/8 · " + L18n.localize("railroad.activity.eta.seconds", 6L),
                    0.25);
                return null;
            });
            third.close();
            first.close();
            onFx(() -> {
                assertTrue(pane.getTaskOverlay().isVisible());
                assertEquals(1, rows.getChildren().size());
                assertEquals(L18n.localize("railroad.activity.none_running"),
                    ((Label) rows.getChildren().getFirst()).getText());
                assertTrue(pane.isVisible());
                assertFalse(bar(pane).isVisible());
                return null;
            });
            try (var next = activities.start("railroad.activity.indexing_libraries")) {
                next.indeterminate("next library");
                onFx(() -> {
                    assertTaskRow(rows, 0, "next library", "", -1);
                    pane.fire();
                    assertFalse(pane.getTaskOverlay().isManaged());
                    assertFalse(pane.getTaskOverlay().isVisible());
                    return null;
                });
            }
        } finally {
            onFx(() -> {
                stage.getScene().setRoot(new Pane());
                stage.close();
                return null;
            });
        }
    }

    private static void assertTaskRow(VBox rows, int index, String phase, String detail, double fraction) {
        var row = rows.getChildren().get(index);
        assertEquals(fraction, ((ProgressBar) row.lookup(".progress-bar")).getProgress());
        var text = row.lookupAll(".label").stream().map(node -> ((Label) node).getText()).toList();
        assertTrue(text.contains(phase));
        assertTrue(text.contains(detail));
    }

    private static ProgressBar bar(IDEActivityPane pane) {
        return (ProgressBar) pane.getGraphic().lookup(".progress-bar");
    }

    private static List<String> labels(IDEActivityPane pane) {
        return ((HBox) pane.getGraphic()).getChildren().stream()
            .filter(node -> node.isManaged() && node instanceof Label)
            .map(node -> ((Label) node).getText())
            .map(text -> text == null ? "" : text).toList();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        // Runs after any refresh the pane queued, since JavaFX runs runLater tasks in order.
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
