package dev.railroadide.railroad.debug.breakpoint;

/**
 * Receives synchronous notifications of shared breakpoint mutations.
 */
@FunctionalInterface
public interface BreakpointListener {
    /**
     * Handles a breakpoint mutation on the thread that changed the store.
     *
     * @param event mutation already applied to the breakpoint store
     */
    void onBreakpointEvent(BreakpointEvent event);
}
