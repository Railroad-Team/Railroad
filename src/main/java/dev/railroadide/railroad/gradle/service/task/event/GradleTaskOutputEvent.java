package dev.railroadide.railroad.gradle.service.task.event;

import dev.railroadide.railroad.gradle.service.task.GradleTaskState;

import java.util.UUID;

/**
 * Represents a chunk of Gradle output emitted while a task is running.
 *
 * @param taskId      the identifier of the running task
 * @param state       the current state when the output was produced
 * @param output      the actual text emitted
 */
public record GradleTaskOutputEvent(
    UUID taskId,
    GradleTaskState state,
    String output) {
}
