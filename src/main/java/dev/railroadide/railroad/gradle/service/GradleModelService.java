package dev.railroadide.railroad.gradle.service;

import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.task.GradleTaskModel;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the background import of Gradle models and exposes task metadata.
 */
public interface GradleModelService {

    /**
     * Refreshes the Gradle model, optionally forcing a rebuild even if cached data exists.
     *
     * @param force whether to bypass caches when refreshing the model
     * @return a future that completes with the refreshed model
     */
    CompletableFuture<GradleBuildModel> refreshModel(boolean force);

    /**
     * @return the last successful Gradle build model that was loaded
     */
    Optional<GradleBuildModel> getCachedModel();

    /**
     * @return every task from the most recently cached model, or an empty list if no model is loaded
     */
    default List<GradleTaskModel> getAllTasks() {
        return getCachedModel().map(GradleBuildModel::projects)
            .stream()
            .flatMap(projects -> projects.stream()
                .flatMap(project -> project.tasks().stream()))
            .toList();
    }

    /**
     * Finds tasks that match the provided name.
     *
     * @param name the task name to search for
     * @return tasks whose simple name matches the provided string
     */
    default List<GradleTaskModel> findTasksByName(String name) {
        return getAllTasks().stream()
            .filter(task -> task.name().equals(name))
            .toList();
    }

    /**
     * Looks up a task by its full Gradle path.
     *
     * @param path the fully-qualified task path
     * @return the matching task if it exists
     */
    default Optional<GradleTaskModel> findTaskByPath(String path) {
        return getAllTasks().stream()
            .filter(task -> task.path().equals(path))
            .findFirst();
    }
}
