package dev.railroadide.railroad.debug.model;

/**
 * A snapshot of a target thread and its JDI execution status.
 *
 * @param id JDI unique thread identifier
 * @param name target thread name
 * @param status status code defined by JDI ThreadReference
 * @param suspended whether JDI reports the thread as suspended
 */
public record DebugThread(
    long id,
    String name,
    int status,
    boolean suspended
) {
}
