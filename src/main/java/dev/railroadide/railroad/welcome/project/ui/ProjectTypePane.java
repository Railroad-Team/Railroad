package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.welcome.project.ProjectType;
import dev.railroadide.railroad.welcome.project.ui.widget.ProjectTypeCell;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import lombok.Getter;

public class ProjectTypePane extends RRVBox {
    private final ScrollPane projectTypesScroller;
    @Getter
    private final RRListView<ProjectType> projectTypeListView;
    private final ObservableList<ProjectType> allProjectTypes = FXCollections.observableArrayList(ProjectType.values());

    public ProjectTypePane() {
        super(16);

        setPadding(new Insets(18, 18, 18, 18));
        getStyleClass().add("project-type-pane");

        setMinWidth(240);
        setMaxWidth(320);

        projectTypesScroller = new ScrollPane();
        projectTypesScroller.setFitToWidth(true);
        projectTypesScroller.setFitToHeight(true);
        projectTypesScroller.getStyleClass().add("project-types-scroller");

        projectTypeListView = new RRListView<>();
        projectTypeListView.getStyleClass().add("project-type-list");
        projectTypeListView.setCellFactory(param -> new ProjectTypeCell());
        projectTypeListView.getItems().addAll(allProjectTypes);
        projectTypeListView.getSelectionModel().selectFirst();
        projectTypeListView.setListViewSize(RRListView.ListViewSize.MEDIUM);
        projectTypeListView.setDense(true);
        projectTypeListView.setBordered(true);
        projectTypeListView.setPrefHeight(220);
        projectTypeListView.setFocusTraversable(false);
        projectTypeListView.getStyleClass().add("hide-empty-cells");

        projectTypesScroller.setContent(projectTypeListView);
        projectTypesScroller.setFitToWidth(true);
        projectTypesScroller.setFitToHeight(true);
        projectTypesScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        projectTypesScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        projectTypesScroller.getStyleClass().add("project-types-scroller");

        getChildren().addAll(projectTypesScroller);
        RRVBox.setVgrow(projectTypesScroller, Priority.ALWAYS);
    }
}