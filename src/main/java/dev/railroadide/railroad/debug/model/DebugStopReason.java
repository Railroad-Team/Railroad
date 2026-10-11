package dev.railroadide.railroad.debug.model;

/**
 * Identifies the event that suspended debugger execution.
 */
public enum DebugStopReason {
    /**
     * A runtime breakpoint request stopped execution.
     */
    BREAKPOINT,
    /**
     * A line-step request stopped execution.
     */
    STEP,
    /**
     * The user explicitly paused the target virtual machine.
     */
    PAUSE,
    /**
     * The target stopped at virtual-machine startup.
     */
    ENTRY
}
