package dev.railroadide.railroad.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Manages the list of projects and the currently open project.
 */
@Getter
public final class ProjectManager {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private final Runnable configSaver;
    @Getter
    private Project openProject;

    /**
     * Creates a new ProjectManager that saves the configuration using ConfigHandler::saveConfig.
     */
    public ProjectManager() {
        this(ConfigHandler::saveConfig);
    }

    /**
     * Creates a new ProjectManager with a custom configuration saver.
     *
     * @param configSaver the Runnable to save the configuration
     */
    public ProjectManager(Runnable configSaver) {
        this.configSaver = Objects.requireNonNull(configSaver, "configSaver");
    }

    /**
     * Updates the project information in the list of projects. If the project already exists, it updates the last
     * opened time.
     * If it does not exist, it adds the new project to the list.
     *
     * @param project the project to update or add
     * @return the updated or newly added project
     */
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

    /**
     * Finds a project by its path.
     *
     * @param path the path of the project to find
     * @return an Optional containing the found project, or empty if not found
     */
    public Optional<Project> findProject(Path path) {
        String pathKey = ProjectPathIdentityUtils.key(path);
        return projects.stream()
            .filter(project -> ProjectPathIdentityUtils.key(project.getPath()).equals(pathKey))
            .findFirst();
    }

    /**
     * Sets the list of projects, ensuring that only the most recently opened project for each unique path is kept.
     *
     * @param projectCollection the collection of projects to set
     */
    public void setProjects(Collection<? extends Project> projectCollection) {
        Objects.requireNonNull(projectCollection, "projectCollection");

        Map<String, Project> projectsByPath = new LinkedHashMap<>();
        for (Project project : projectCollection) {
            if (project == null)
                continue;

            String pathKey = ProjectPathIdentityUtils.key(project.getPath());
            Project existing = projectsByPath.get(pathKey);
            if (existing == null || project.getLastOpened() > existing.getLastOpened()) {
                projectsByPath.put(pathKey, project);
            }
        }

        this.projects.setAll(projectsByPath.values());
    }

    /**
     * Adds a new project or updates an existing one in the list of projects.
     *
     * @param project the project to add or update
     * @return the updated or newly added project
     */
    public Project newProject(Project project) {
        return updateProjectInfo(project);
    }

    /**
     * Removes a project from the list of projects.
     *
     * @param project the project to remove
     */
    public void removeProject(Project project) {
        Objects.requireNonNull(project, "project");
        Railroad.LOGGER.info("Removing project: {}", project.getId());
        String pathKey = ProjectPathIdentityUtils.key(project.getPath());
        projects.removeIf(projectObj -> ProjectPathIdentityUtils.key(projectObj.getPath()).equals(pathKey));
        configSaver.run();
    }

    /**
     * Sets the currently open project and publishes a ProjectEvent to the event bus.
     *
     * @param project the project to set as currently open, or null to indicate no project is open
     */
    public void setCurrentProject(@Nullable Project project) {
        Project beingClosed = this.openProject;
        this.openProject = project;

        Railroad.EVENT_BUS.publish(new ProjectEvent(
            beingClosed == null ? project : beingClosed,
            project == null ? ProjectEvent.EventType.CLOSED : ProjectEvent.EventType.OPENED));
    }
}
