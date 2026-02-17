package dev.railroadide.railroad.vcs.git.execution.progress;

/**
 * Cancellation token consulted while running long-lived git commands.
 */
public interface GitCancellationToken {
    boolean isCancellationRequested();
}
