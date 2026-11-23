package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;
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
                var statusBar = new IDEStatusBarPane();
                var scene = new Scene(statusBar, 900, 32);
                try {
                    statusBar.applyCss();
                    statusBar.layout();
                    var activity = statusBar.lookup(".rr-activity-pane");
                    var caret = statusBar.lookup("IDECaretPositionPane");
                    assertTrue(activity.isManaged());
                    assertEquals(caret.getBoundsInParent().getMinX(),
                        activity.getBoundsInParent().getMaxX() + statusBar.getSpacing(), 0.5);
                    assertEquals(statusBar.getWidth(), caret.getBoundsInParent().getMaxX(), 0.5);
                } finally {
                    scene.setRoot(new Pane());
                }
            }
            return null;
        });
    }

    private static ProgressBar bar(IDEActivityPane pane) {
        return (ProgressBar) pane.lookup(".progress-bar");
    }

    private static List<String> labels(IDEActivityPane pane) {
        return pane.getChildren().stream().filter(node -> node.isManaged() && node instanceof Label)
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
