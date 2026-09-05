package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRCard;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.scene.layout.Priority;

/** Project creation card pairing project-type selection with the selected type's onboarding interface. */
public class NewProjectPane extends RRCard {
    private final ProjectTypePane projectTypePane;
    private final ProjectDetailsPane projectDetailsPane;

    /**
     * Builds the type selector and details pane, binds their selection, and registers the UI identifier while attached.
     */
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

        Services.UI_MANAGER.assignWhileAttached(UIIds.Welcome.NEW_PROJECT, this);
    }
}
