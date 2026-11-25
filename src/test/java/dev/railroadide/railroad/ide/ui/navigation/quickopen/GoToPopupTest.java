package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class GoToPopupTest {
    @TempDir
    public Path directory;
    private Stage stage;
    private StackPane root;

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
    public void closeStage() throws Exception {
        onFx(() -> {
            if (stage != null) {
                stage.close();
            }
            return null;
        });
    }

    @Test
    public void typingRanksTheCollectedItems() throws Exception {
        var contributor = new NamesContributor(List.of("FindReplaceBar", "FindQuery", "EditorTab"));
        GoToPopup<String> popup = show(contributor);

        onFx(() -> {
            assertEquals(List.of("EditorTab", "FindQuery", "FindReplaceBar"), names(popup));
            popup.searchField().setText("frb");
            assertEquals(List.of("FindReplaceBar"), names(popup));
            popup.searchField().setText("find");
            assertEquals(List.of("FindQuery", "FindReplaceBar"), names(popup));
            popup.searchField().setText("zzz");
            assertTrue(popup.results().isEmpty());
            return null;
        });
    }

    @Test
    public void enterOpensTheSelectedItemWithTheLineSuffix() throws Exception {
        var contributor = new NamesContributor(List.of("FindReplaceBar", "FindQuery"));
        GoToPopup<String> popup = show(contributor);

        onFx(() -> {
            popup.searchField().setText("find:12");
            press(popup.searchField(), KeyCode.DOWN);
            press(popup.searchField(), KeyCode.ENTER);

            assertFalse(popup.isShowing());
            return null;
        });

        assertEquals(List.of("FindReplaceBar@12"), contributor.opened);
    }

    @Test
    public void selectionStaysWithinTheResults() throws Exception {
        var contributor = new NamesContributor(List.of("Alpha", "AlphaBeta"));
        GoToPopup<String> popup = show(contributor);

        onFx(() -> {
            popup.searchField().setText("alpha");
            press(popup.searchField(), KeyCode.UP);
            press(popup.searchField(), KeyCode.PAGE_DOWN);
            press(popup.searchField(), KeyCode.ENTER);
            return null;
        });

        assertEquals(List.of("AlphaBeta@0"), contributor.opened);
    }

    @Test
    public void showingAnotherPopupClosesTheFirstAndCancelsItsCollection() throws Exception {
        var blocked = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        var slow = new NamesContributor(List.of()) {
            @Override
            public List<String> collect(Project project, Runnable activityCheck) throws InterruptedException {
                blocked.countDown();
                while (true) {
                    try {
                        activityCheck.run();
                    } catch (RuntimeException exception) {
                        cancelled.countDown();
                        throw exception;
                    }
                    Thread.sleep(5);
                }
            }
        };
        GoToPopup<String> first = onFx(() -> {
            createStage();
            return GoToPopup.show(root, project(), slow);
        });
        assertTrue(blocked.await(10, TimeUnit.SECONDS));

        GoToPopup<String> second = onFx(() -> GoToPopup.show(root, project(),
            new NamesContributor(List.of("Other"))));

        assertTrue(cancelled.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            assertFalse(first.isShowing());
            assertTrue(second.isShowing());
            second.hide();
            return null;
        });
    }

    private GoToPopup<String> show(NamesContributor contributor) throws Exception {
        GoToPopup<String> popup = onFx(() -> {
            createStage();
            return GoToPopup.show(root, project(), contributor);
        });
        assertNotNull(popup);
        popup.loaded().get(10, TimeUnit.SECONDS);
        return popup;
    }

    private void createStage() {
        root = new StackPane();
        stage = new Stage();
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    private Project project() {
        return new RailroadProject(directory, "test", new WritableImage(1, 1));
    }

    private static List<String> names(GoToPopup<String> popup) {
        return popup.results().stream().map(GoToSearch.Result::item).toList();
    }

    private static void press(Node target, KeyCode code) {
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread())
            return action.call();
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static class NamesContributor implements GoToContributor<String> {
        private final List<String> items;
        private final List<String> opened = new CopyOnWriteArrayList<>();

        private NamesContributor(List<String> items) {
            this.items = items;
        }

        @Override
        public String titleKey() {
            return "railroad.ide.go_to.file.title";
        }

        @Override
        public List<String> collect(Project project, Runnable activityCheck) throws Exception {
            return items;
        }

        @Override
        public String name(String item) {
            return item;
        }

        @Override
        public String path(String item) {
            return item;
        }

        @Override
        public String detail(String item) {
            return "";
        }

        @Override
        public char separator() {
            return '/';
        }

        @Override
        public Node icon(String item) {
            return new StackPane();
        }

        @Override
        public void open(String item, GoToSearch.Query query) {
            opened.add(item + "@" + query.line());
        }
    }
}
