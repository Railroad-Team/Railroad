package io.github.railroad.project.ui.project.newProject;

import io.github.railroad.project.ProjectType;
import javafx.scene.control.SplitPane;

public class NewProjectPane extends SplitPane {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    public NewProjectPane() {
        projectTypePane = new ProjectTypePane();
        projectTypePane.setMinWidth(200);

        projectDetailsPane = new ProjectDetailsPane();
        projectDetailsPane.setMinWidth(600);

        getItems().addAll(projectTypePane, projectDetailsPane);

        projectTypePane.getProjectTypeListView().setOnMouseClicked(event -> {
            ProjectType selected = projectTypePane.getProjectTypeListView().getSelectionModel().getSelectedItem();
            projectDetailsPane.projectTypeProperty().set(selected);
        });
    }
}