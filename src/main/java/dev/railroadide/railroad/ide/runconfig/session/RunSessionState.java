package dev.railroadide.railroad.ide.runconfig.session;

/**
 * Lifecycle of a run session. Every state other than {@link #RUNNING} is final.
 */
public enum RunSessionState {
    /**
     * The session is building, launching or running its process.
     */
    RUNNING,
    /**
     * The run finished normally with exit code zero, or without an exit code when none applies.
     */
    SUCCEEDED,
    /**
     * The run could not start, failed, or exited with a non-zero exit code.
     */
    FAILED,
    /**
     * The run ended after the user asked it to stop.
     */
    STOPPED;

    /**
     * Tests whether the session has ended.
     *
     * @return whether this state is final
     */
    public boolean isTerminal() {
        return this != RUNNING;
    }
}
