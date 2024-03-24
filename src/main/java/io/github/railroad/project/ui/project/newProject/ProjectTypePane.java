package io.github.railroad.project.ui.project.newProject;

import io.github.railroad.project.ProjectType;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ProjectTypePane extends VBox {
    private final ProjectTypeSearchField projectTypeSearchField;
    private final ScrollPane projectTypesScroller;
    private final ListView<ProjectType> projectTypeListView;

    public ProjectTypePane() {
        projectTypeSearchField = new ProjectTypeSearchField();

        projectTypesScroller = new ScrollPane();
        projectTypesScroller.setFitToWidth(true);
        projectTypesScroller.setFitToHeight(true);

        projectTypeListView = new ListView<>();
        projectTypeListView.setCellFactory(param -> new ProjectTypeCell());
        projectTypeListView.getItems().addAll(ProjectType.values());

        projectTypesScroller.setContent(projectTypeListView);

        getChildren().addAll(projectTypeSearchField, projectTypesScroller);
        VBox.setVgrow(projectTypesScroller, Priority.ALWAYS);
    }

    public ProjectTypeSearchField getProjectTypeSearchField() {
        return projectTypeSearchField;
    }

    public ListView<ProjectType> getProjectTypeListView() {
        return projectTypeListView;
    }
}
