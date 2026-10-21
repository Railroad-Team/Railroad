package dev.railroadide.railroad.debug.model;

/**
 * The socket address of a virtual machine accepting debugger connections.
 *
 * @param host debug target hostname or address
 * @param port debug target socket port
 */
public record DebugEndpoint(String host, int port) {
}
