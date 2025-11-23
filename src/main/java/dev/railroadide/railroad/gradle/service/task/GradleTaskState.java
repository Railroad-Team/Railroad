package dev.railroadide.railroad.gradle.service.task;

/**
 * Represents the lifecycle stages of a Gradle task execution.
 */
public enum GradleTaskState {
    QUEUED,
    STARTING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;
}
