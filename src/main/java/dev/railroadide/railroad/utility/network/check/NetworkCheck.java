package dev.railroadide.railroad.utility.network.check;

/**
 * A synchronous probe for a particular aspect of network reachability.
 * Implementations define the address format, success criteria, and handling of invalid inputs.
 */
public interface NetworkCheck {
    /**
     * Probes the supplied target on the calling thread.
     * The timeout applies according to the implementation and need not bound the entire operation.
     *
     * @param address target in the format required by the implementation
     * @param timeout timeout in milliseconds, with valid values defined by the implementation
     * @return true if the probe's success criteria are met, or false if the probe fails
     */
    boolean check(String address, int timeout);

    /**
     * Controls whether a probe logs the exceptions it handles.
     * Implementations may override this method to suppress failure logging.
     *
     * @return true by default to enable failure logging
     */
    default boolean shouldLogFailures() {
        return true;
    }
}
