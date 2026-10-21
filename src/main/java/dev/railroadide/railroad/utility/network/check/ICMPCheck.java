package dev.railroadide.railroad.utility.network.check;

import dev.railroadide.railroad.Railroad;

import java.net.InetAddress;

/**
 * Tests host reachability through {@link InetAddress#isReachable(int)}.
 * The underlying reachability mechanism depends on the platform and available privileges.
 */
public class ICMPCheck implements NetworkCheck {
    /** Creates a host reachability probe with failure logging enabled. */
    public ICMPCheck() {
    }

    /**
     * Resolves a host and asks the platform to test its reachability.
     * Resolution and reachability exceptions, including invalid timeout values, produce false and are logged
     * when {@link #shouldLogFailures()} is true. Host resolution precedes the timed reachability operation.
     *
     * @param address hostname or IP address to resolve
     * @param timeout nonnegative reachability timeout in milliseconds
     * @return true if the resolved host is reachable, or false if it is unreachable or an exception occurs
     */
    @Override
    public boolean check(String address, int timeout) {
        try {
            InetAddress inet = InetAddress.getByName(address);
            return inet.isReachable(timeout);
        } catch (Exception exception) {
            if (shouldLogFailures()) {
                Railroad.LOGGER.error("ICMP check failed for address: {}", address, exception);
            }

            return false;
        }
    }
}
