package io.github.railroad.project.ui.welcome;

import io.github.railroad.project.Project;
import io.github.railroad.project.ProjectSort;
import io.github.railroad.project.ui.project.ProjectListCell;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;

public class WelcomeProjectsPane extends ScrollPane {
    private final ListView<Project> projectsList = new ListView<>();
    private ObservableValue<ProjectSort> sortProperty;

    public WelcomeProjectsPane() {
        setStyle("-fx-background-color: #f0f0f0;");
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        projectsList.setCellFactory(param -> new ProjectListCell());

        projectsList.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2)
                return;

            Project project = projectsList.getSelectionModel().getSelectedItem();
            if (project != null) {
                project.open();
            }
        });

        projectsList.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Project project = projectsList.getSelectionModel().getSelectedItem();
                if (project != null) {
                    project.open();
                }

                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                Project project = projectsList.getSelectionModel().getSelectedItem();
                if (project != null) {
                    project.delete(false); // TODO: Ask for confirmation
                    removeProject(project);
                }

                event.consume();
            }
        });

        projectsList.getItems().addAll(Project.loadProjects());

        setContent(projectsList);
    }

    public void addProject(Project project) {
        projectsList.getItems().add(project);
    }

    public void removeProject(Project project) {
        projectsList.getItems().remove(project);
    }

    public void setSortProperty(ObservableValue<ProjectSort> observable) {
        sortProperty = observable;

        observable.addListener((observableValue, oldValue, newValue) -> sortProjects(newValue));
        projectsList.getItems().addListener((ListChangeListener<Project>) c -> sortProjects(observable.getValue()));
        sortProjects(observable.getValue());
    }

    private void sortProjects(ProjectSort sort) {
        List<Project> copy = new ArrayList<>(List.copyOf(projectsList.getItems()));
        copy.sort(sort.getComparator());

        if (copy.equals(projectsList.getItems()))
            return;

        projectsList.getItems().setAll(copy);
    }
}