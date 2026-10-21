package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.railroad.project.ProjectType;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.welcome.project.ui.widget.ProjectTypeCell;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Priority;
import lombok.Getter;
import javafx.scene.layout.VBox;

/** Scrollable selector populated from the project types registered when this pane is created. */
public class ProjectTypePane extends VBox {
    /**
     * List exposing the project-type selection used by the onboarding pane.
     *
     * @return the live project-type list
     */
    @Getter
    private final RRListView<ProjectType> projectTypeListView;
    private final ObservableList<ProjectType> allProjectTypes = FXCollections
        .observableArrayList(ProjectType.REGISTRY.values());

    /** Builds the type list with icon cells and selects its first entry, if any. */
    public ProjectTypePane() {
        super();
        getStyleClass().add("project-type-pane");

        projectTypeListView = new RRListView<>();
        projectTypeListView.setAnimationsEnabled(false);
        projectTypeListView.getStyleClass().add("project-type-list");
        projectTypeListView.setCellFactory(_ -> new ProjectTypeCell());
        projectTypeListView.getItems().addAll(allProjectTypes);
        projectTypeListView.getSelectionModel().selectFirst();
        projectTypeListView.setListViewSize(RRListView.ListViewSize.MEDIUM);
        projectTypeListView.setDense(true);
        projectTypeListView.setBordered(false);
        projectTypeListView.setFocusTraversable(true);
        projectTypeListView.setFixedCellSize(36);
        projectTypeListView.getStyleClass().add("hide-empty-cells");

        getChildren().add(projectTypeListView);
        setVgrow(projectTypeListView, Priority.ALWAYS);
    }
}
