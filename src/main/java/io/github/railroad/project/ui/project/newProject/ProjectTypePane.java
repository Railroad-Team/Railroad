package io.github.railroad.project.ui.project.newProject;

import io.github.railroad.project.ProjectType;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class ProjectTypePane extends VBox {
    private final Button backButton;
    private final ProjectTypeSearchField projectTypeSearchField;
    private final ScrollPane projectTypesScroller;
    private final ListView<ProjectType> projectTypeListView;

    public ProjectTypePane() {
        backButton = new Button("Back");
        backButton.setGraphic(new FontIcon(FontAwesomeSolid.BACKSPACE));
        backButton.setPrefWidth(200);

        projectTypeSearchField = new ProjectTypeSearchField();

        projectTypesScroller = new ScrollPane();
        projectTypesScroller.setFitToWidth(true);
        projectTypesScroller.setFitToHeight(true);

        projectTypeListView = new ListView<>();
        projectTypeListView.setCellFactory(param -> new ProjectTypeCell());
        projectTypeListView.getItems().addAll(ProjectType.values());
        projectTypeListView.getSelectionModel().selectFirst();

        projectTypesScroller.setContent(projectTypeListView);

        getChildren().addAll(backButton, projectTypeSearchField, projectTypesScroller);
        VBox.setVgrow(projectTypesScroller, Priority.ALWAYS);
    }

    public ProjectTypeSearchField getProjectTypeSearchField() {
        return projectTypeSearchField;
    }

    public ListView<ProjectType> getProjectTypeListView() {
        return projectTypeListView;
    }

    public Button getBackButton() {
        return backButton;
    }
}
