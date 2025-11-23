package dev.railroadide.railroad.gradle.service.task.event;

import dev.railroadide.railroad.gradle.service.task.GradleTaskState;

import java.util.UUID;

/**
 * Indicates a status change for a running Gradle task.
 *
 * @param taskId      the execution identifier
 * @param state       the current lifecycle stage
 * @param messageKey  optional contextual text localization key
 * @param messageArgs optional arguments for the localization text
 */
public record GradleTaskStatusEvent(
    UUID taskId,
    GradleTaskState state,
    String messageKey,
    Object[] messageArgs) {
}
