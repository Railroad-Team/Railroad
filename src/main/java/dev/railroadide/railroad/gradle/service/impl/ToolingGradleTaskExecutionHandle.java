package dev.railroadide.railroad.gradle.service.impl;

import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionHandle;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionResult;
import dev.railroadide.railroad.gradle.service.task.GradleTaskState;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskErrorEvent;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskOutputEvent;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskProgressEvent;
import dev.railroadide.railroad.gradle.service.task.event.GradleTaskStatusEvent;
import org.gradle.tooling.CancellationTokenSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Implementation of {@link GradleTaskExecutionHandle} for managing the execution of a Gradle task.
 */
public class ToolingGradleTaskExecutionHandle implements GradleTaskExecutionHandle {
    private final UUID id = UUID.randomUUID();
    private final GradleTaskExecutionRequest request;

    private final List<Consumer<GradleTaskProgressEvent>> progressListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<GradleTaskOutputEvent>> outputListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<GradleTaskErrorEvent>> errorListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<GradleTaskStatusEvent>> statusListeners = new CopyOnWriteArrayList<>();

    private volatile GradleTaskState state = GradleTaskState.QUEUED;
    private volatile CompletableFuture<GradleTaskExecutionResult> resultCompletion = new CompletableFuture<>();

    private volatile CancellationTokenSource cancellationTokenSource;
    private volatile int debugPort = -1;

    private final Queue<String> outputBuffer = new ConcurrentLinkedQueue<>();
    private final Queue<String> errorBuffer = new ConcurrentLinkedQueue<>();

    /**
     * Creates a new ToolingGradleTaskExecutionHandle for the given request.
     *
     * @param request the Gradle task execution request
     */
    ToolingGradleTaskExecutionHandle(GradleTaskExecutionRequest request) {
        this.request = request;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public GradleTaskExecutionRequest request() {
        return request;
    }

    @Override
    public GradleTaskState currentState() {
        return state;
    }

    @Override
    public Optional<Integer> debugPort() {
        return debugPort >= 0 ? Optional.of(debugPort) : Optional.empty();
    }

    /**
     * Attaches a CancellationTokenSource to this execution handle.
     *
     * @param tokenSource the CancellationTokenSource to attach
     */
    void attachCancellationToken(CancellationTokenSource tokenSource) {
        this.cancellationTokenSource = tokenSource;
    }

    /**
     * Sets the debug port for this execution handle.
     *
     * @param port the debug port number
     */
    void setDebugPort(int port) {
        this.debugPort = port;
    }

    @Override
    public void cancel() {
        CancellationTokenSource src = this.cancellationTokenSource;
        if (src != null) {
            src.cancel();
        }
    }

    @Override
    public void onProgressChanged(Consumer<GradleTaskProgressEvent> listener) {
        progressListeners.add(listener);
    }

    @Override
    public void onOutputReceived(Consumer<GradleTaskOutputEvent> listener) {
        outputListeners.add(listener);
    }

    @Override
    public void onErrorReceived(Consumer<GradleTaskErrorEvent> listener) {
        errorListeners.add(listener);
    }

    @Override
    public void onStatusChanged(Consumer<GradleTaskStatusEvent> listener) {
        statusListeners.add(listener);
    }

    @Override
    public CompletableFuture<GradleTaskExecutionResult> completionFuture() {
        return resultCompletion;
    }

    /**
     * Updates the current state of the task and notifies listeners.
     *
     * @param newState    the new state of the task
     * @param messageKey  the message key for status updates
     * @param messageArgs optional arguments for the message
     */
    void updateState(GradleTaskState newState, String messageKey, Object... messageArgs) {
        this.state = newState;

        var progressEvent = new GradleTaskProgressEvent(
            id,
            newState,
            messageKey,
            messageArgs,
            -1.0, // unknown progress for now
            Instant.now()
        );
        for (Consumer<GradleTaskProgressEvent> listener : progressListeners) {
            listener.accept(progressEvent);
        }

        var statusEvent = new GradleTaskStatusEvent(id, newState, messageKey, messageArgs);
        for (var listener : statusListeners) {
            listener.accept(statusEvent);
        }
    }

    /**
     * Emits an output line and notifies listeners.
     *
     * @param output the output line
     */
    void emitOutput(String output) {
        outputBuffer.add(output);

        var outputEvent = new GradleTaskOutputEvent(id, state, output);
        for (var listener : outputListeners) {
            listener.accept(outputEvent);
        }
    }

    /**
     * Emits an error line and notifies listeners.
     *
     * @param error the error line
     */
    void emitError(String error) {
        errorBuffer.add(error);

        var errorEvent = new GradleTaskErrorEvent(id, state, error);
        for (var listener : errorListeners) {
            listener.accept(errorEvent);
        }
    }

    /**
     * Sets the result future for this execution handle.
     *
     * @param future the CompletableFuture representing the task result
     */
    void setResultFuture(CompletableFuture<GradleTaskExecutionResult> future) {
        this.resultCompletion = future;
    }

    /**
     * Gets the output buffer.
     *
     * @return the output buffer
     */
    Queue<String> getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Gets the error buffer.
     *
     * @return the error buffer
     */
    Queue<String> getErrorBuffer() {
        return errorBuffer;
    }
}
