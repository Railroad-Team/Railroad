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
    private final ObservableList<RailroadProject> projects = FXCollections.observableArrayList();
    @Getter
    private RailroadProject openProject;

    public void updateProjectInfo(RailroadProject project) {
        updateProjectInfo(project, false);
    }

    public void updateProjectInfo(RailroadProject project, boolean removeProject) {
        Railroad.LOGGER.info("Starting project update: {}", project.getId());
        boolean found = false;
        if (removeProject) {
            Railroad.LOGGER.info("Removing project: {}", project.getId());
            projects.removeIf(projectObj -> projectObj.getId().equals(project.getId()));
            ConfigHandler.saveConfig();
            return;
        }

        for (RailroadProject projectObj : projects) {
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

    public void setProjects(Collection<? extends RailroadProject> projectCollection) {
        this.projects.setAll(projectCollection);
    }

    public RailroadProject newProject(RailroadProject project) {
        updateProjectInfo(project);
        return project;
    }

    public void removeProject(RailroadProject project) {
        updateProjectInfo(project, true);
    }

    public void setCurrentProject(@Nullable RailroadProject project) {
        this.openProject = project;
    }
}
