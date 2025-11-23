package dev.railroadide.railroad.gradle.service.task.event;

import dev.railroadide.railroad.gradle.service.task.GradleTaskState;

import java.time.Instant;
import java.util.UUID;

/**
 * Signals incremental progress from a running Gradle task.
 *
 * @param taskId      the execution identifier
 * @param state       the current task state
 * @param messageKey  an optional message describing the progress, identified by a localization key
 * @param messageArgs optional arguments for the progress message
 * @param progress    a fraction between {@code 0.0} and {@code 1.0}
 * @param timestamp   when the progress event was emitted
 */
public record GradleTaskProgressEvent(UUID taskId, GradleTaskState state, String messageKey, Object[] messageArgs,
                                      double progress, Instant timestamp) {
}
