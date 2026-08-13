package dev.railroadide.railroad.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

@Getter
public final class ProjectManager {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private final Runnable configSaver;
    @Getter
    private Project openProject;

    public ProjectManager() {
        this(ConfigHandler::saveConfig);
    }

    ProjectManager(Runnable configSaver) {
        this.configSaver = Objects.requireNonNull(configSaver, "configSaver");
    }

    public Project updateProjectInfo(Project project) {
        Objects.requireNonNull(project, "project");
        Railroad.LOGGER.info("Starting project update: {}", project.getId());

        Optional<Project> existingProject = findProject(project.getPath());
        if (existingProject.isPresent()) {
            Project existing = existingProject.get();
            existing.setLastOpened(project.getLastOpened());
            Railroad.LOGGER.info("Updated project: {} last opened to: {}", existing.getId(), project.getLastOpened());
            configSaver.run();
            return existing;
        }

        Railroad.LOGGER.info("Creating new project entry for: {}", project.getPath());
        projects.add(project);
        configSaver.run();
        return project;
    }

    public Optional<Project> findProject(Path path) {
        String pathKey = ProjectPathIdentity.key(path);
        return projects.stream()
            .filter(project -> ProjectPathIdentity.key(project.getPath()).equals(pathKey))
            .findFirst();
    }

    public void setProjects(Collection<? extends Project> projectCollection) {
        Objects.requireNonNull(projectCollection, "projectCollection");

        Map<String, Project> projectsByPath = new LinkedHashMap<>();
        for (Project project : projectCollection) {
            if (project == null)
                continue;

            String pathKey = ProjectPathIdentity.key(project.getPath());
            Project existing = projectsByPath.get(pathKey);
            if (existing == null || project.getLastOpened() > existing.getLastOpened()) {
                projectsByPath.put(pathKey, project);
            }
        }

        this.projects.setAll(projectsByPath.values());
    }

    public Project newProject(Project project) {
        return updateProjectInfo(project);
    }

    public void removeProject(Project project) {
        Objects.requireNonNull(project, "project");
        Railroad.LOGGER.info("Removing project: {}", project.getId());
        String pathKey = ProjectPathIdentity.key(project.getPath());
        projects.removeIf(projectObj -> ProjectPathIdentity.key(projectObj.getPath()).equals(pathKey));
        configSaver.run();
    }

    public void setCurrentProject(@Nullable Project project) {
        Project beingClosed = this.openProject;
        this.openProject = project;

        Railroad.EVENT_BUS.publish(new ProjectEvent(
            beingClosed == null ? project : beingClosed,
            project == null ? ProjectEvent.EventType.CLOSED : ProjectEvent.EventType.OPENED
        ));
    }
}
