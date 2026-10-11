package dev.railroadide.railroad.debug.breakpoint;

/**
 * Describes a mutation published after the shared breakpoint store has been updated.
 */
public sealed interface BreakpointEvent {
    /**
     * Reports a newly stored source breakpoint.
     *
     * @param breakpoint newly added breakpoint definition
     */
    record Added(SourceBreakpoint breakpoint) implements BreakpointEvent {
    }

    /**
     * Reports a source breakpoint removed from the store.
     *
     * @param breakpoint definition that was removed
     */
    record Removed(SourceBreakpoint breakpoint) implements BreakpointEvent {
    }

    /**
     * Reports an updated definition of an existing source breakpoint.
     *
     * @param breakpoint updated breakpoint definition
     */
    record Changed(SourceBreakpoint breakpoint) implements BreakpointEvent {
    }
}
