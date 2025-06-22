package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.ui.ImportProjectListCell;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRStackPane;
import io.github.railroad.vcs.Repository;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;

public class WelcomeImportProjectsPane extends ScrollPane {
    private final RRListView<Repository> repositoryListView = new RRListView<>();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    public WelcomeImportProjectsPane() {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        repositoryListView.setCellFactory(param -> new ImportProjectListCell());

        repositoryListView.setOnMouseClicked(event -> {
            // Handle mouse click events here
        });

        repositoryListView.setOnKeyReleased(event -> {
            // Handle key release events here
        });

        // Bind the visibility of the progress indicator to the emptiness of the repository list
        progressIndicator.visibleProperty().bind(Bindings.isEmpty(Railroad.REPOSITORY_MANAGER.getRepositories()));

        // Set the items of the repository list view
        repositoryListView.setItems(Railroad.REPOSITORY_MANAGER.getRepositories());

        // Create a StackPane to hold both the ListView and the ProgressIndicator
        var stackPane = new RRStackPane();
        stackPane.getChildren().addAll(repositoryListView, progressIndicator);

        setContent(stackPane);
    }
}
