package dev.railroadide.railroad.welcome;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.ui.nodes.ProjectListCell;
import dev.railroadide.railroad.welcome.project.ProjectSort;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pane that displays the list of projects in the welcome screen.
 */
public class WelcomeProjectsPane extends ScrollPane {
    private final RRListView<Project> projectsList = new RRListView<>();

    private ObservableValue<ProjectSort> sortProperty;

    private static volatile boolean isProcessingClick = false;

    public WelcomeProjectsPane(RRTextField searchField) {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        searchField.textProperty().addListener(obs -> {
            String filter = searchField.getText();
            filterProjects(filter);
        });

        projectsList.getStyleClass().add("welcome-projects-list");
        projectsList.setCellFactory(param -> new ProjectListCell());

        projectsList.setFocusTraversable(false);

        projectsList.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2)
                return;

            if (isProcessingClick) {
                return; // Prevent rapid successive clicks
            }

            isProcessingClick = true;
            
            try {
                Project project = projectsList.getSelectionModel().getSelectedItem();
                if (project != null) {
                    project.open();
                }
            } finally {
                // Reset the flag after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {
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
                        project.open();
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
        Railroad.PROJECT_MANAGER.getProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    projectsList.getItems().addAll(c.getAddedSubList());
                } else if (c.wasRemoved()) {
                    projectsList.getItems().removeAll(c.getRemoved());
                }
            }
            updateEmptyState();
        });

        filterProjects("");
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (projectsList.getItems().isEmpty()) {
            // Show empty state illustration and message
            var emptyBox = new VBox(12);
            emptyBox.setAlignment(javafx.geometry.Pos.CENTER);
            emptyBox.setPadding(new javafx.geometry.Insets(40, 0, 40, 0));
            var illustration = new ImageView(new Image(Railroad.getResourceAsStream("images/logo.png"), 96, 96, true, true));
            var message = new LocalizedLabel("railroad.home.welcome.projects.empty");
            message.getStyleClass().add("welcome-projects-message");
            emptyBox.getChildren().addAll(illustration, message);
            setContent(emptyBox);
        } else {
            setContent(projectsList);
        }
    }

    /**
     * Removes a project from the projects list.
     * 
     * @param project the project to remove
     */
    public void removeProject(Project project) {
        projectsList.getItems().remove(project);
    }

    /**
     * Filters the projects list based on the provided search value.
     * Projects whose alias contains the search value (case-insensitive) will be displayed.
     * 
     * @param value the search term to filter projects by
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
     * The projects will be automatically sorted when the sort property changes or when new projects are added.
     * 
     * @param observable the observable value containing the sort criteria
     */
    public void setSortProperty(ObservableValue<ProjectSort> observable) {
        this.sortProperty = observable;

        this.sortProperty.addListener((observableValue, oldValue, newValue) -> sortProjects(newValue));
        projectsList.getItems().addListener((ListChangeListener<Project>) c -> sortProjects(this.sortProperty.getValue()));
        sortProjects(this.sortProperty.getValue());
    }

    private void sortProjects(ProjectSort sort) {
        List<Project> copy = new ArrayList<>(projectsList.getItems());
        if (sort == null) return;
        copy.sort(sort.getComparator());

        if (copy.equals(projectsList.getItems()))
            return;

        projectsList.getItems().setAll(FXCollections.observableArrayList(copy));
    }
}