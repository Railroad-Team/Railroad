package dev.railroadide.railroad.gradle.service.task;

import dev.railroadide.railroad.gradle.service.task.event.GradleTaskErrorEvent;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskOutputEvent;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskProgressEvent;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskStatusEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Tracks a running Gradle task and exposes APIs for observation and control.
 */
public interface GradleTaskExecutionHandle {

    /**
     * @return the unique identifier assigned to this execution.
     */
    UUID id();

    /**
     * @return the request that produced this execution handle.
     */
    GradleTaskExecutionRequest request();

    /**
     * @return the current state of the Gradle task execution
     */
    GradleTaskState currentState();

    /**
     * @return the debug port if the task is running in debug mode, otherwise empty
     */
    Optional<Integer> debugPort();

    /**
     * Cancels the execution asynchronously.
     */
    void cancel();

    /**
     * Registers a listener for progress updates emitted by Gradle.
     *
     * @param listener the consumer that handles progress events
     */
    void onProgressChanged(Consumer<GradleTaskProgressEvent> listener);

    /**
     * Registers a listener for standard output produced by Gradle.
     *
     * @param listener the consumer that handles output chunks
     */
    void onOutputReceived(Consumer<GradleTaskOutputEvent> listener);

    /**
     * Registers a listener for errors emitted by Gradle.
     *
     * @param listener the consumer that handles error events
     */
    void onErrorReceived(Consumer<GradleTaskErrorEvent> listener);

    /**
     * Registers a listener for status changes (queued, running, completed, etc.).
     *
     * @param listener the consumer that handles status events
     */
    void onStatusChanged(Consumer<GradleTaskStatusEvent> listener);

    /**
     * @return a future that completes when the task finishes, either successfully or with a failure
     */
    CompletableFuture<GradleTaskExecutionResult> completionFuture();
}
