package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class IDEActivityPaneTest {
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
    public void showsNewestActivityWithCountOfOthersAndHidesWhenIdle() throws Exception {
        var activities = new BackgroundActivities();
        IDEActivityPane pane = onFx(() -> {
            var created = new IDEActivityPane(activities);
            new Scene(created);
            return created;
        });
        assertFalse(onFx(pane::isVisible));

        var importing = activities.start("railroad.activity.importing_gradle");
        var indexing = activities.start("railroad.activity.indexing_libraries");
        List<String> shown = onFx(() -> labels(pane));
        assertTrue(onFx(pane::isVisible));
        assertEquals(List.of(L18n.localize("railroad.activity.indexing_libraries"),
            L18n.localize("railroad.activity.others", 1)), shown);

        indexing.close();
        assertEquals(List.of(L18n.localize("railroad.activity.importing_gradle"), ""), onFx(() -> labels(pane)));
        importing.close();
        assertFalse(onFx(pane::isVisible));
    }

    private static List<String> labels(IDEActivityPane pane) {
        return pane.getChildren().stream().filter(Label.class::isInstance).map(node -> ((Label) node).getText())
            .map(text -> text == null ? "" : text).toList();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        // Runs after any refresh the pane queued, since JavaFX runs runLater tasks in order.
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
