package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.ui.nodes.RRCard;
import io.github.railroad.ui.nodes.ImportProjectListCell;
import io.github.railroad.ui.nodes.RRListView;
import io.github.railroad.vcs.Repository;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import lombok.Getter;

@Getter
public class WelcomeImportProjectsPane extends RRCard {
    private final RRListView<Repository> repositoryListView = new RRListView<>();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    public WelcomeImportProjectsPane() {
        super(18, new Insets(24, 32, 24, 32));
        setSpacing(18);
        getStyleClass().add("welcome-card");

        repositoryListView.setCellFactory(param -> new ImportProjectListCell());
        repositoryListView.setItems(Railroad.REPOSITORY_MANAGER.getRepositories());
        repositoryListView.setPrefHeight(400);

        progressIndicator.visibleProperty().bind(Bindings.isEmpty(Railroad.REPOSITORY_MANAGER.getRepositories()));
        progressIndicator.setMaxSize(48, 48);

        var stackPane = new StackPane(repositoryListView, progressIndicator);
        StackPane.setAlignment(progressIndicator, Pos.CENTER);
        stackPane.setPadding(new Insets(8, 0, 8, 0));

        getChildren().add(stackPane);
    }
}
