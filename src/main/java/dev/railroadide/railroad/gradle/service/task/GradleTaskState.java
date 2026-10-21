package dev.railroadide.railroad.gradle.service.task;

/**
 * Represents the lifecycle stages of a Gradle task execution.
 */
public enum GradleTaskState {
    /**
     * The task has been queued for execution but has not yet started.
     */
    QUEUED,
    /**
     * The task is in the process of starting.
     */
    STARTING,
    /**
     * The task is currently running.
     */
    RUNNING,
    /**
     * The task has finished execution successfully.
     */
    COMPLETED,
    /**
     * The task has finished execution with a failure.
     */
    FAILED,
    /**
     * The task execution was cancelled before completion.
     */
    CANCELLED;
}
