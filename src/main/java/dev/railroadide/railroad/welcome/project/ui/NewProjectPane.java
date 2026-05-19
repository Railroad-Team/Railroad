package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.railroad.ui.RRCard;
import dev.railroadide.railroad.ui.RRHBox;
import javafx.scene.layout.Priority;

public class NewProjectPane extends RRCard {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    public NewProjectPane() {
        super(18);
        getStyleClass().add("new-project-pane");

        projectTypePane = new ProjectTypePane();
        projectDetailsPane = new ProjectDetailsPane();

        var contentBox = new RRHBox();
        contentBox.getStyleClass().add("new-project-content-box");
        contentBox.getChildren().addAll(projectTypePane, projectDetailsPane);
        RRHBox.setHgrow(projectDetailsPane, Priority.ALWAYS);

        getChildren().add(contentBox);

        projectDetailsPane.projectTypeProperty().bind(
            projectTypePane.getProjectTypeListView().getSelectionModel().selectedItemProperty());
    }
}
