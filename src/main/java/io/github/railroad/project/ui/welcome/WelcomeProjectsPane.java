package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.project.Project;
import io.github.railroad.project.ProjectSort;
import io.github.railroad.project.ui.project.ProjectListCell;
import io.github.railroad.project.ui.project.ProjectSearchField;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WelcomeProjectsPane extends ScrollPane {
    private final ListView<Project> projectsList = new ListView<>();

    private ObservableValue<ProjectSort> sortProperty;

    public WelcomeProjectsPane(ProjectSearchField searchField) {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        searchField.textProperty().addListener(obs->{
            String filter = searchField.getText();
            filterProjects(filter);
        });

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
                    Railroad.PROJECT_MANAGER.removeProject(project, true);
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
        });

        filterProjects("");
        setContent(projectsList);
    }

    public void removeProject(Project project) {
        projectsList.getItems().remove(project);
    }

    public void filterProjects(String value) {
        projectsList.getItems().clear();

        if (value == null || value.isEmpty()) {
            projectsList.getItems().addAll(Railroad.PROJECT_MANAGER.getProjects());
            return;
        }

        List<Project> filteredProjects = new ArrayList<>();
        for (Project project : Railroad.PROJECT_MANAGER.getProjects()) {
            if (project.getAlias().toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) {
                filteredProjects.add(project);
            }
        }

        projectsList.getItems().addAll(filteredProjects);
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