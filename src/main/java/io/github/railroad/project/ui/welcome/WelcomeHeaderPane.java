package io.github.railroad.project.ui.welcome;

import io.github.railroad.project.ui.OpenProjectButton;
import io.github.railroad.project.ui.ImportProjectButton;
import io.github.railroad.project.ui.NewProjectButton;
import io.github.railroad.project.ui.project.ProjectSearchField;
import io.github.railroad.project.ui.project.ProjectSortComboBox;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class WelcomeHeaderPane extends HBox {
    private final ProjectSearchField searchField;
    private final ProjectSortComboBox sortComboBox;
    private final NewProjectButton newProjectButton;
    private final OpenProjectButton openProjectButton;
    private final ImportProjectButton importProjectButton;

    public WelcomeHeaderPane() {
        searchField = new ProjectSearchField();
        getChildren().add(searchField);

        HBox.setHgrow(searchField, Priority.ALWAYS);

        sortComboBox = new ProjectSortComboBox();
        getChildren().add(sortComboBox);

        newProjectButton = new NewProjectButton();
        getChildren().add(newProjectButton);

        openProjectButton = new OpenProjectButton();
        getChildren().add(openProjectButton);

        importProjectButton = new ImportProjectButton();
        getChildren().add(importProjectButton);

        setSpacing(10);
        setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0;");
        setAlignment(Pos.CENTER_LEFT);
    }

    public ProjectSearchField getSearchField() {
        return searchField;
    }

    public ProjectSortComboBox getSortComboBox() {
        return sortComboBox;
    }

    public NewProjectButton getNewProjectButton() {
        return newProjectButton;
    }

    public OpenProjectButton getOpenProjectButton() {
        return openProjectButton;
    }

    public ImportProjectButton getVCSProjectButton() {
        return importProjectButton;
    }
}