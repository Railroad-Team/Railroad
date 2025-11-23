package dev.railroadide.railroad.gradle.model.task;

import java.util.List;

/**
 * Describes a single argument that can be passed to a Gradle task.
 *
 * @param name         the CLI name of the argument
 * @param displayName  a user-friendly label for the argument
 * @param type         how the argument value should be handled
 * @param defaultValue the default value to use if the argument is omitted
 * @param description  explanatory text about the argument
 * @param enumValues   candidate values when {@link GradleTaskArgType#ENUM} is used
 */
public record GradleTaskArgument(String name, String displayName, GradleTaskArgType type, String defaultValue,
                                 String description, List<String> enumValues) {
}
