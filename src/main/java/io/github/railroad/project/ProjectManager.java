package io.github.railroad.project;

import io.github.railroad.Railroad;
import io.github.railroad.project.data.Project;
import io.github.railroad.config.ConfigHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.Collection;

@Getter
public final class ProjectManager {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();

    public void updateProjectInfo(Project project) {
        updateProjectInfo(project, false);
    }

    public void updateProjectInfo(Project project, boolean removeProject) {
        Railroad.LOGGER.info("Starting project update: {}", project.getId());
        boolean found = false;
        if (removeProject) {
            Railroad.LOGGER.info("Removing project: {}", project.getId());
            projects.removeIf(projectObj -> projectObj.getId().equals(project.getId()));
            ConfigHandler.updateConfig();
            return;
        }

        for (Project projectObj : projects) {
            if (projectObj.getId().equals(project.getId())) {
                found = true;
                projectObj.setLastOpened(project.getLastOpened());
                Railroad.LOGGER.info("Starting update project: {} last opened to: {}", project.getId(), project.getLastOpened());
            }
        }

        if (!found) {
            Railroad.LOGGER.info("Create new Project");
            projects.add(project);
        }
        
        ConfigHandler.updateConfig();
    }

    public void setProjects(Collection<? extends Project> projectCollection) {
        this.projects.setAll(projectCollection);
    }

    public Project newProject(Project project) {
        updateProjectInfo(project);
        return project;
    }

    public void removeProject(Project project) {
        updateProjectInfo(project, true);
    }
}
