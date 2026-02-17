package dev.railroadide.railroad.vcs.git.execution;

/**
 * Runtime exception thrown when a git command cannot be executed successfully.
 */
public class GitExecutionException extends RuntimeException {
    /**
     * Creates an execution exception with a message.
     *
     * @param message failure description
     */
    public GitExecutionException(String message) {
        super(message);
    }

    /**
     * Creates an execution exception with a message and cause.
     *
     * @param message failure description
     * @param cause underlying error
     */
    public GitExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
