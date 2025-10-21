package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRHBox;
import javafx.geometry.Insets;
import javafx.scene.layout.Priority;

public class NewProjectPane extends RRCard {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    public NewProjectPane() {
        super(18, new Insets(24, 32, 24, 32));
        setSpacing(18);
        getStyleClass().add("new-project-pane");

        projectTypePane = new ProjectTypePane();
        projectDetailsPane = new ProjectDetailsPane();

        var contentBox = new RRHBox(0);
        contentBox.getChildren().addAll(projectTypePane, projectDetailsPane);
        RRHBox.setHgrow(projectDetailsPane, Priority.ALWAYS);

        getChildren().add(contentBox);

        projectDetailsPane.projectTypeProperty().bind(
            projectTypePane.getProjectTypeListView().getSelectionModel().selectedItemProperty());
    }
}
