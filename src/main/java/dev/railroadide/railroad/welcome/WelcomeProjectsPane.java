package dev.railroadide.railroad.welcome;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.nodes.ProjectListCell;
import dev.railroadide.railroad.welcome.project.ProjectSort;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pane that displays the list of projects in the welcome screen.
 */
public class WelcomeProjectsPane extends ScrollPane {
    private static volatile boolean isProcessingClick = false;
    private final RRListView<Project> projectsList = new RRListView<>();
    private ObservableValue<ProjectSort> sortProperty;

    /**
     * Builds a browser of known projects with opening and removal actions and an empty-state display.
     * Initially displays all projects, then filters when the supplied search field changes.
     *
     * @param searchField field whose text changes supply the alias filter
     */
    public WelcomeProjectsPane(RRTextField searchField) {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        searchField.textProperty().addListener(_ -> {
            String filter = searchField.getText();
            filterProjects(filter);
        });

        projectsList.getStyleClass().add("welcome-projects-list");
        projectsList.setCellFactory(_ -> new ProjectListCell());

        projectsList.setFocusTraversable(false);

        projectsList.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2)
                return;

            if (isProcessingClick)
                return; // Prevent rapid successive clicks

            isProcessingClick = true;

            try {
                Project project = projectsList.getSelectionModel().getSelectedItem();
                if (project != null) {
                    project.open(null);
                }
            } finally {
                // Reset the flag after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } finally {
                        isProcessingClick = false;
                    }
                }).start();
            }
        });

        projectsList.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (isProcessingClick)
                    return;

                isProcessingClick = true;

                try {
                    Project project = projectsList.getSelectionModel().getSelectedItem();
                    if (project != null) {
                        project.open(null);
                    }
                } finally {
                    // Reset the flag after a short delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            isProcessingClick = false;
                        }
                    }).start();
                }

                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                Project project = projectsList.getSelectionModel().getSelectedItem();
                if (project != null) {
                    Railroad.PROJECT_MANAGER.removeProject(project);
                    filterProjects("");
                }

                event.consume();
            }
        });

        this.projectsList.getItems().addAll(Railroad.PROJECT_MANAGER.getProjects());
        Railroad.PROJECT_MANAGER.getProjects().addListener((ListChangeListener<Project>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    projectsList.getItems().addAll(change.getAddedSubList());
                } else if (change.wasRemoved()) {
                    projectsList.getItems().removeAll(change.getRemoved());
                }
            }
            updateEmptyState();
        });

        filterProjects("");
        updateEmptyState();

        Services.UI_MANAGER.assignWhileAttached(UIIds.Welcome.WELCOME_PROJECTS, this);
    }

    private void updateEmptyState() {
        if (projectsList.getItems().isEmpty()) {
            // Show empty state illustration and message
            var emptyBox = new RRVBox();
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.getStyleClass().add("welcome-projects-empty-box");
            var illustration = new ImageView(new Image(AppResources.iconStream(), 96, 96, true, true));
            var message = new LocalizedLabel("railroad.home.welcome.projects.empty");
            message.getStyleClass().add("welcome-projects-message");
            emptyBox.getChildren().addAll(illustration, message);
            setContent(emptyBox);
        } else {
            setContent(projectsList);
        }
    }

    /**
     * Filters the projects list based on the provided search value.
     * Projects whose alias contains the search value (case-insensitive) will be displayed.
     *
     * @param value the search term to filter projects by, or null or empty to display all known projects
     */
    public void filterProjects(String value) {
        projectsList.getItems().clear();

        if (value == null || value.isEmpty()) {
            projectsList.getItems().addAll(Railroad.PROJECT_MANAGER.getProjects());
            updateEmptyState();
            return;
        }

        List<Project> filteredProjects = new ArrayList<>();
        for (Project project : Railroad.PROJECT_MANAGER.getProjects()) {
            if (project.getAlias().toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) {
                filteredProjects.add(project);
            }
        }

        projectsList.getItems().addAll(filteredProjects);
        updateEmptyState();
    }

    /**
     * Sets the sort property for the projects list.
     * Sorts the current items immediately and listens for sort-value changes and changes to the current item list.
     * Sorting may replace the item list, so the installed list listener does not follow subsequent replacements.
     *
     * @param observable the observable value containing the sort criteria
     */
    public void setSortProperty(ObservableValue<ProjectSort> observable) {
        this.sortProperty = observable;

        this.sortProperty.addListener((_, _, newValue) -> sortProjects(newValue));
        projectsList.getItems()
            .addListener((ListChangeListener<Project>) _ -> sortProjects(this.sortProperty.getValue()));
        sortProjects(this.sortProperty.getValue());
    }

    private void sortProjects(ProjectSort sort) {
        List<Project> copy = new ArrayList<>(projectsList.getItems());
        if (sort == null)
            return;

        copy.sort(sort.getComparator());

        if (copy.equals(projectsList.getItems()))
            return;

        projectsList.setItems(FXCollections.observableArrayList(copy));
    }
}
