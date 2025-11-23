package dev.railroadide.railroad.gradle.service.task;

/**
 * Summarizes the outcome of a finished Gradle execution.
 *
 * @param state       the final state of the task
 * @param exitCode    the OS exit code returned by Gradle
 * @param output      the captured standard output stream
 * @param errorOutput the captured error stream
 */
public record GradleTaskExecutionResult(
    GradleTaskState state,
    int exitCode,
    String output,
    String errorOutput) {
}
