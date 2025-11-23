package dev.railroadide.railroad.gradle.service;

import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionHandle;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;

import java.util.List;

/**
 * Responsible for scheduling Gradle task executions and exposing their state.
 */
public interface GradleExecutionService {

    /**
     * Begins execution of the provided request.
     *
     * @param request the Gradle task request to execute
     * @return a handle that can be used to observe and control the running task
     */
    GradleTaskExecutionHandle runTask(GradleTaskExecutionRequest request);

    /**
     * @return the handles for tasks currently running through this service
     */
    List<GradleTaskExecutionHandle> getRunningTasks();

    /**
     * Stops every task that was started through this service.
     *
     * @return handles to the tasks that were requested to stop
     */
    List<GradleTaskExecutionHandle> stopAllRunningTasks();

    /**
     * @return the last few task execution requests submitted to this service
     */
    List<GradleTaskExecutionRequest> getRecentRequests();
}
