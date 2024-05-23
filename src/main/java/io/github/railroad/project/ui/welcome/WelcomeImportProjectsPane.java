package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.project.ui.project.ImportProjectListCell;
import io.github.railroad.vcs.Repository;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;

public class WelcomeImportProjectsPane extends ScrollPane {
    private final ListView<Repository> repositoryListView = new ListView<>();

    public WelcomeImportProjectsPane() {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        repositoryListView.setCellFactory(param -> new ImportProjectListCell());

        repositoryListView.setOnMouseClicked(event -> {

        });

        repositoryListView.setOnKeyReleased(event -> {
        });
        repositoryListView.getItems().addAll(Railroad.REPOSITORY_MANAGER.getRepositoryList());
        setContent(repositoryListView);
    }
}
