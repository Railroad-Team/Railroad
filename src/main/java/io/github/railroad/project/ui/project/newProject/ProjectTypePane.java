package io.github.railroad.project.ui.project.newProject;

import io.github.railroad.project.ProjectType;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class ProjectTypePane extends RRVBox {
    private final Button backButton;
    private final ProjectTypeSearchField projectTypeSearchField;
    private final ScrollPane projectTypesScroller;
    private final RRListView<ProjectType> projectTypeListView;

    public ProjectTypePane() {
        setMinWidth(200);
        setMaxWidth(200);

        backButton = new Button("Back");
        backButton.setGraphic(new FontIcon(FontAwesomeSolid.BACKSPACE));
        backButton.prefWidthProperty().bind(widthProperty());

        projectTypeSearchField = new ProjectTypeSearchField();

        projectTypesScroller = new ScrollPane();
        projectTypesScroller.setFitToWidth(true);
        projectTypesScroller.setFitToHeight(true);

        projectTypeListView = new RRListView<>();
        projectTypeListView.setCellFactory(param -> new ProjectTypeCell());
        projectTypeListView.getItems().addAll(ProjectType.values());
        projectTypeListView.getSelectionModel().selectFirst();

        projectTypesScroller.setContent(projectTypeListView);

        getChildren().addAll(backButton, projectTypeSearchField, projectTypesScroller);
        RRVBox.setVgrow(projectTypesScroller, Priority.ALWAYS);
    }

    public ProjectTypeSearchField getProjectTypeSearchField() {
        return projectTypeSearchField;
    }

    public RRListView<ProjectType> getProjectTypeListView() {
        return projectTypeListView;
    }

    public Button getBackButton() {
        return backButton;
    }
}
