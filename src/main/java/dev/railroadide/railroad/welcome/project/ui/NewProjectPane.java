package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;

/** Project creation workspace pairing project-type selection with the selected type's onboarding interface. */
public class NewProjectPane extends HBox {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    /**
     * Builds the type selector and details pane, binds their selection, and registers the UI identifier while attached.
     */
    public NewProjectPane() {
        super();
        getStyleClass().add("new-project-pane");

        projectTypePane = new ProjectTypePane();
        projectDetailsPane = new ProjectDetailsPane();

        var contentBox = new HBox();
        contentBox.setMinWidth(0);
        HBox.setHgrow(contentBox, Priority.ALWAYS);
        contentBox.getStyleClass().add("new-project-content-box");
        contentBox.getChildren().addAll(projectTypePane, projectDetailsPane);
        HBox.setHgrow(projectDetailsPane, Priority.ALWAYS);

        getChildren().add(contentBox);

        projectDetailsPane.projectTypeProperty().bind(
            projectTypePane.getProjectTypeListView().getSelectionModel().selectedItemProperty());

        Services.UI_MANAGER.assignWhileAttached(UIIds.Welcome.NEW_PROJECT, this);
    }
}
