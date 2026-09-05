package dev.railroadide.railroad.utility.network;

import dev.railroadide.railroad.utility.network.check.HTTPCheck;
import dev.railroadide.railroad.utility.network.check.ICMPCheck;
import dev.railroadide.railroad.utility.network.check.TCPCheck;

/**
 * Dispatches synchronous network reachability probes by protocol.
 * HTTP probes require an HTTP or HTTPS URL; host probes accept a hostname or IP address.
 * These operations block the calling thread and should run outside the JavaFX application thread.
 */
public class NetworkChecker {
    /** Shared checker using the default implementations of each probe. */
    public static final NetworkChecker INSTANCE = new NetworkChecker();

    private final ICMPCheck icmpCheck = new ICMPCheck();
    private final HTTPCheck httpCheck = new HTTPCheck();
    private final TCPCheck tcpCheck = new TCPCheck();

    /** Creates a checker with host reachability, URL-connection HTTP, and TCP port-53 probes. */
    public NetworkChecker() {
    }

    /**
     * Runs the selected probe and returns its result. Failures handled by the probe are logged.
     * The timeout is passed to the probe's network operations and is not an overall deadline including DNS lookup.
     *
     * @param address HTTP or HTTPS URL for {@link CheckMode#HTTP}, or hostname or IP address for the other modes
     * @param mode probe to execute
     * @param timeout timeout in milliseconds; zero means no timeout for the default HTTP and TCP probes
     * @return true if the selected probe succeeds, or false for a failed probe or a handled exception
     * @throws NullPointerException if {@code mode} is null
     * @throws IllegalArgumentException if the mode is unsupported or the HTTP probe rejects its configuration
     */
    public boolean check(String address, CheckMode mode, int timeout) {
        return switch (mode) {
            case ICMP -> icmpCheck.check(address, timeout);
            case HTTP -> httpCheck.check(address, timeout);
            case TCP -> tcpCheck.check(address, timeout);
            default -> throw new IllegalArgumentException("Unsupported check mode: " + mode);
        };
    }
}
