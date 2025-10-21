package dev.railroadide.railroad.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@Getter
public final class ProjectManager {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    @Getter
    private Project openProject;

    public void updateProjectInfo(Project project) {
        updateProjectInfo(project, false);
    }

    public void updateProjectInfo(Project project, boolean removeProject) {
        Railroad.LOGGER.info("Starting project update: {}", project.getId());
        boolean found = false;
        if (removeProject) {
            Railroad.LOGGER.info("Removing project: {}", project.getId());
            projects.removeIf(projectObj -> projectObj.getId().equals(project.getId()));
            ConfigHandler.saveConfig();
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

        ConfigHandler.saveConfig();
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

    public void setCurrentProject(@Nullable Project project) {
        this.openProject = project;
    }
}
