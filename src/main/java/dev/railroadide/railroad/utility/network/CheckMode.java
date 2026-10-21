package dev.railroadide.railroad.utility.network;

/** Selects the reachability probe used by {@link NetworkChecker}. */
public enum CheckMode {
    /** Uses {@link java.net.InetAddress#isReachable(int)} to test host reachability. */
    ICMP,
    /** Sends an HTTP HEAD request using the default URL-connection client. */
    HTTP,
    /** Attempts a TCP connection to port 53 without exchanging DNS messages. */
    TCP
}
