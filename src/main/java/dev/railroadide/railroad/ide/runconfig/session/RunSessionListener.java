package dev.railroadide.railroad.ide.runconfig.session;

/**
 * Receives the output and state of a run session.
 * <p>
 * Callbacks run on whichever thread produced the event while the session's lock is held, so they arrive in the order
 * the session recorded them. Implementations must return quickly and must not call back into the session; hand the
 * event to another thread, such as the JavaFX thread, instead.
 */
public interface RunSessionListener {
    /**
     * Called for each recorded chunk of output.
     *
     * @param output the recorded chunk
     */
    void outputAppended(RunOutput output);

    /**
     * Called once when the session reaches its final state.
     *
     * @param state the final state
     */
    void sessionEnded(RunSessionState state);
}
