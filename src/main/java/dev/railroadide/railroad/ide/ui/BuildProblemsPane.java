package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.diagnostics.BuildProblems;
import dev.railroadide.railroad.ide.ui.navigation.quickopen.GoToContributor;
import dev.railroadide.railroad.ide.ui.navigation.quickopen.GoToSearch;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;

import java.nio.file.Files;
import java.nio.file.Path;

/** Project build errors, with navigation to the compiler's one-based source position. */
public final class BuildProblemsPane extends BorderPane implements AutoCloseable {
    private final ListView<BuildProblems.Problem> rows = new ListView<>();
    private final Runnable unsubscribe;
    private boolean closed;

    public BuildProblemsPane(Project project) {
        var problems = project.getRunSessions().buildProblems();
        Path root = project.getPath().toAbsolutePath().normalize();
        rows.setPlaceholder(new LocalizedLabel("railroad.ide.problems.empty"));
        rows.setCellFactory(_ -> new ListCell<>() {
            {
                setOnMouseClicked(event -> {
                    if (!isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                        open(getItem());
                    }
                });
            }

            @Override
            protected void updateItem(BuildProblems.Problem problem, boolean empty) {
                super.updateItem(problem, empty);
                if (empty || problem == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                Path file = problem.file();
                String location = file == null
                    ? ""
                    : (file.startsWith(root) ? root.relativize(file) : file)
                        + ":" + problem.line() + ":" + problem.column() + "  ";
                String summary = problem.message().lines().findFirst().orElse(problem.message());
                setText(location + summary);
                setTooltip(new Tooltip(location + problem.message()));
            }
        });
        rows.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && rows.getSelectionModel().getSelectedItem() != null) {
                open(rows.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });
        getStyleClass().add("build-problems-pane");
        setCenter(rows);
        unsubscribe = problems.subscribe(next -> Platform.runLater(() -> {
            if (closed || problems.problems() != next)
                return;
            boolean newErrors = !rows.getItems().containsAll(next);
            rows.getItems().setAll(next);
            if (newErrors && Services.IDE_STATE.getCurrentProject() == project) {
                Services.UI_MANAGER.lookup(UIIds.IDE.IDE).ifPresent(ide -> ide.revealDockItem(IDEDockItem.PROBLEMS));
            }
        }));
    }

    private static void open(BuildProblems.Problem problem) {
        if (problem.file() != null && Files.isRegularFile(problem.file())) {
            var position = new GoToSearch.Query("", problem.line(), problem.column());
            GoToContributor.openInEditor(problem.file(), position::offsetIn);
        }
    }

    @Override
    public void close() {
        closed = true;
        unsubscribe.run();
    }
}
