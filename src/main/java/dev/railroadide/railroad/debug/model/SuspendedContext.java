package dev.railroadide.railroad.debug.model;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.EventSet;

import java.util.Map;

/**
 * Retains the JDI objects and resume mechanism for the current suspension.
 *
 * @param generation identifier for this suspension
 * @param eventSet event set to resume, or null for an explicit pause
 * @param explicitVmSuspend whether resuming must balance an explicit virtual-machine suspension
 * @param eventThread thread associated with the stop, or null when unavailable
 * @param threads target threads indexed by their unique JDI identifiers
 */
public record SuspendedContext(
    long generation,
    EventSet eventSet,
    boolean explicitVmSuspend,
    ThreadReference eventThread,
    Map<Long, ThreadReference> threads
) {
}
