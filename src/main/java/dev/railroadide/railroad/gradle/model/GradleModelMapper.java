package dev.railroadide.railroad.gradle.model;

import dev.railroadide.railroad.gradle.model.task.GradleTaskArgument;
import dev.railroadide.railroad.gradle.model.task.GradleTaskModel;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

import java.util.*;

/**
 * Utility class for mapping Gradle project and task models.
 */
public class GradleModelMapper {

    /**
     * Maps a Gradle project to a GradleProjectModel without task arguments.
     *
     * @param project the Gradle project to map.
     * @return the mapped GradleProjectModel.
     */
    public static GradleProjectModel mapProject(GradleProject project) {
        return mapProject(project, Collections.emptyMap());
    }

    /**
     * Maps a Gradle project to a GradleProjectModel, including task arguments.
     *
     * @param project         the Gradle project to map.
     * @param argumentsByPath a map of task paths to their respective arguments.
     * @return the mapped GradleProjectModel.
     */
    public static GradleProjectModel mapProject(GradleProject project, Map<String, List<GradleTaskArgument>> argumentsByPath) {
        List<GradleTaskModel> tasks = new ArrayList<>();
        for (GradleTask task : project.getTasks()) {
            tasks.add(mapTask(task, argumentsByPath));
        }

        return new GradleProjectModel(
            project.getPath(),
            project.getName(),
            project.getProjectDirectory().toPath(),
            tasks
        );
    }

    /**
     * Collects Gradle projects into a list of GradleProjectModel without task arguments.
     *
     * @param project   the root Gradle project.
     * @param collected the list to collect the mapped GradleProjectModel instances.
     */
    public static void collectProjects(GradleProject project, List<GradleProjectModel> collected) {
        collectProjects(project, collected, Collections.emptyMap());
    }

    /**
     * Collects Gradle projects into a list of GradleProjectModel, including task arguments.
     *
     * @param project         the root Gradle project.
     * @param collected       the list to collect the mapped GradleProjectModel instances.
     * @param argumentsByPath a map of task paths to their respective arguments.
     */
    public static void collectProjects(GradleProject project, List<GradleProjectModel> collected, Map<String, List<GradleTaskArgument>> argumentsByPath) {
        collected.add(mapProject(project, argumentsByPath));
        for (GradleProject child : project.getChildren()) {
            collectProjects(child, collected, argumentsByPath);
        }
    }

    /**
     * Maps a Gradle task to a GradleTaskModel, including task arguments.
     *
     * @param task            the Gradle task to map.
     * @param argumentsByPath a map of task paths to their respective arguments.
     * @return the mapped GradleTaskModel.
     */
    public static GradleTaskModel mapTask(GradleTask task, Map<String, List<GradleTaskArgument>> argumentsByPath) {
        return new GradleTaskModel(
            task.getPath(),
            task.getName(),
            task.getGroup(),
            task.getDescription() != null ? task.getDescription() : "",
            task.getGroup() != null ? task.getGroup() : "",
            isIncrementalSafe(task),
            argumentsByPath.getOrDefault(task.getPath(), List.of())
        );
    }

    /**
     * Determines if a Gradle task is incremental-safe.
     * A task is considered incremental-safe if its name does not start with "clean".
     * <p>
     * TODO: Improve this logic to accurately reflect task incremental safety based on more criteria.
     *
     * @param task the Gradle task to check.
     * @return true if the task is incremental-safe, false otherwise.
     */
    private static boolean isIncrementalSafe(GradleTask task) {
        String name = task.getName().toLowerCase(Locale.ROOT);
        // Very naive: non-clean tasks are assumed to be incremental-safe
        return !name.startsWith("clean");
    }
}
