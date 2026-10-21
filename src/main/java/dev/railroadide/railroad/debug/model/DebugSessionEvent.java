package dev.railroadide.railroad.debug.model;

import dev.railroadide.railroad.plugin.spi.event.Event;

/**
 * A lifecycle notification emitted by a debugger session.
 */
public sealed interface DebugSessionEvent extends Event {
    /**
     * Reports a transition between debugger lifecycle states.
     *
     * @param oldState state before the transition
     * @param newState state after the transition
     */
    record StateChanged(DebugSessionState oldState, DebugSessionState newState) implements DebugSessionEvent {
    }

    /**
     * Reports stopped execution and the thread selected for inspection.
     *
     * @param reason cause of the suspension
     * @param threadId selected thread identifier, or -1 when no thread is available
     */
    record Suspended(DebugStopReason reason, long threadId) implements DebugSessionEvent {
    }

    /**
     * Reports that suspended execution has resumed.
     */
    record Resumed() implements DebugSessionEvent {
    }

    /**
     * Reports a debugger failure together with its underlying cause.
     *
     * @param message human-readable failure description
     * @param cause underlying failure
     */
    record Error(String message, Throwable cause) implements DebugSessionEvent {
    }

    /**
     * Reports the end of a session, including detach or target disconnection.
     */
    record Terminated() implements DebugSessionEvent {
    }
}
