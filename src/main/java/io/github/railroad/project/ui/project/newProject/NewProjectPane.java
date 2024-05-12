package io.github.railroad.project.ui.project.newProject;

import io.github.railroad.project.ProjectType;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;

public class NewProjectPane extends SplitPane {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    public NewProjectPane() {
        projectTypePane = new ProjectTypePane();
        projectDetailsPane = new ProjectDetailsPane();

        getItems().addAll(projectTypePane, projectDetailsPane);

        projectTypePane.getProjectTypeListView().setOnMouseClicked(event -> {
            ProjectType selected = projectTypePane.getProjectTypeListView().getSelectionModel().getSelectedItem();
            projectDetailsPane.projectTypeProperty().set(selected);
        });

        SplitPane.setResizableWithParent(projectTypePane, false);
        SplitPane.setResizableWithParent(projectDetailsPane, true);
    }

    public Button getBackButton() {
        return this.projectTypePane.getBackButton();
    }
}