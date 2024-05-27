package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.project.ui.ImportProjectListCell;
import io.github.railroad.vcs.Repository;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

public class WelcomeImportProjectsPane extends ScrollPane {
    private final ListView<Repository> repositoryListView = new ListView<>();
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
        StackPane stackPane = new StackPane();
        stackPane.getChildren().addAll(repositoryListView, progressIndicator);

        setContent(stackPane);

    }
}
