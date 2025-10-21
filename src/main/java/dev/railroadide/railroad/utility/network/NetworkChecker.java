package dev.railroadide.railroad.utility.network;

import dev.railroadide.railroad.utility.network.check.HTTPCheck;
import dev.railroadide.railroad.utility.network.check.ICMPCheck;
import dev.railroadide.railroad.utility.network.check.TCPCheck;

public class NetworkChecker {
    public static final NetworkChecker INSTANCE = new NetworkChecker();

    private final ICMPCheck icmpCheck = new ICMPCheck();
    private final HTTPCheck httpCheck = new HTTPCheck();
    private final TCPCheck tcpCheck = new TCPCheck();

    public boolean check(String address, CheckMode mode, int timeout) {
        return switch (mode) {
            case ICMP -> icmpCheck.check(address, timeout);
            case HTTP -> httpCheck.check(address, timeout);
            case TCP -> tcpCheck.check(address, timeout);
            default -> throw new IllegalArgumentException("Unsupported check mode: " + mode);
        };
    }
}
