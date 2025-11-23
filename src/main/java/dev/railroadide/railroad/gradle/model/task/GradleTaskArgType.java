package dev.railroadide.railroad.gradle.model.task;

/**
 * Defines how a Gradle task argument value should be interpreted and rendered.
 */
public enum GradleTaskArgType {
    STRING,
    BOOLEAN,
    ENUM,
    FILE,
    DIRECTORY,
    NUMBER
}
