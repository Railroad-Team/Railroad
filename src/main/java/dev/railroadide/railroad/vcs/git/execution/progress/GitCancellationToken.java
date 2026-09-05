package dev.railroadide.railroad.vcs.git.execution.progress;

/**
 * Cancellation token consulted while running long-lived git commands.
 */
public interface GitCancellationToken {
    /**
     * Reports whether the caller has requested cancellation of the running command.
     * Implementations updated from another thread must make those changes visible to the process runner.
     *
     * @return true if the runner should stop the command, or false to let it continue
     */
    boolean isCancellationRequested();
}
