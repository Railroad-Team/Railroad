package dev.railroadide.railroad.gradle.model.task;

import java.util.List;

/**
 * Describes a Gradle task, including metadata required for display and execution.
 *
 * @param path              the full task path (for example {@code :project:task})
 * @param name              the human-readable task name
 * @param group             the Gradle group used to categorize the task
 * @param description       the task description provided by Gradle
 * @param category          a user-friendly category for the task
 * @param isIncrementalSafe whether the task can run incrementally without side effects
 * @param arguments         the arguments that Gradle will accept for this task
 */
public record GradleTaskModel(String path, String name, String group, String description,
                              String category, boolean isIncrementalSafe,
                              List<GradleTaskArgument> arguments) {
}
