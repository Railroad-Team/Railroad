package dev.railroadide.railroad.gradle.service.task.event;

import dev.railroadide.railroad.gradle.service.task.GradleTaskState;

import java.util.UUID;

// TODO: extend with more error details (e.g., exception type, stack trace, etc.)
/**
 * Carries information about an error produced by a running Gradle task.
 *
 * @param taskId       the execution handle identifier
 * @param state        the task’s terminal state when the error occurred
 * @param errorMessage the message provided by Gradle
 */
public record GradleTaskErrorEvent(
    UUID taskId,
    GradleTaskState state,
    String errorMessage) {
}
