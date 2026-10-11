package dev.railroadide.railroad.debug.model;

/**
 * Lifecycle states of a debugger connection and its target execution.
 */
public enum DebugSessionState {
    /**
     * The session has not begun attaching.
     */
    NEW,
    /**
     * The session is connecting to the target virtual machine.
     */
    ATTACHING,
    /**
     * The connection is established and debugger requests are being installed.
     */
    CONFIGURING,
    /**
     * The target is executing and suspended-frame inspection is unavailable.
     */
    RUNNING,
    /**
     * Execution is stopped and suspended threads can be inspected.
     */
    SUSPENDED,
    /**
     * Target termination has been requested and cleanup is in progress.
     */
    TERMINATING,
    /**
     * The session has ended and its inspection handles are invalid.
     */
    TERMINATED,
    /**
     * A debugger operation has failed and the session entered a failure state.
     */
    FAILED
}
