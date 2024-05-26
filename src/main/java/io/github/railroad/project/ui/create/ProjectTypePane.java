package io.github.railroad.project.ui.create;

import io.github.railroad.project.ProjectType;
import io.github.railroad.project.ui.create.widget.ProjectTypeCell;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class ProjectTypePane extends RRVBox {
    private final Button backButton;
    private final TextField projectTypeSearchField;
    private final ScrollPane projectTypesScroller;
    private final RRListView<ProjectType> projectTypeListView;

    public ProjectTypePane() {
        super(10);

        setPadding(new Insets(10));
        getStyleClass().add("project-type-pane");

        setMinWidth(200);
        setMaxWidth(200);

        backButton = new Button("Back");
        backButton.setGraphic(new FontIcon(FontAwesomeSolid.BACKSPACE));
        backButton.prefWidthProperty().bind(widthProperty());

        projectTypeSearchField = new TextField();
        projectTypeSearchField.setPromptText("Search Project Types");

        projectTypesScroller = new ScrollPane();
        projectTypesScroller.setFitToWidth(true);
        projectTypesScroller.setFitToHeight(true);

        projectTypeListView = new RRListView<>();
        projectTypeListView.getStyleClass().add("project-type-list");
        projectTypeListView.setCellFactory(param -> new ProjectTypeCell());
        projectTypeListView.getItems().addAll(ProjectType.values());
        projectTypeListView.getSelectionModel().selectFirst();

        projectTypesScroller.setContent(projectTypeListView);

        var separator = new Separator();
        separator.setPadding(new Insets(0, -10, 0, -10));
        getChildren().addAll(backButton, separator, projectTypeSearchField, projectTypesScroller);
        RRVBox.setVgrow(projectTypesScroller, Priority.ALWAYS);
    }

    public RRListView<ProjectType> getProjectTypeListView() {
        return projectTypeListView;
    }

    public Button getBackButton() {
        return backButton;
    }
}
