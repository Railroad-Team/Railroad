package io.github.railroad.welcome.project.ui;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.welcome.project.ProjectType;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;

public class NewProjectPane extends RRHBox {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    public NewProjectPane() {
        projectTypePane = new ProjectTypePane();
        projectDetailsPane = new ProjectDetailsPane();

        getChildren().addAll(projectTypePane, projectDetailsPane);

        projectTypePane.getProjectTypeListView().setOnMouseClicked(event -> {
            ProjectType selected = projectTypePane.getProjectTypeListView().getSelectionModel().getSelectedItem();
            projectDetailsPane.projectTypeProperty().set(selected);
        });

        RRHBox.setHgrow(projectDetailsPane, Priority.ALWAYS);
    }

    public Button getBackButton() {
        return this.projectTypePane.getBackButton();
    }
}