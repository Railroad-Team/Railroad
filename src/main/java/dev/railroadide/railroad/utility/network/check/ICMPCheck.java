package dev.railroadide.railroad.utility.network.check;

import dev.railroadide.railroad.Railroad;

import java.net.InetAddress;

public class ICMPCheck implements NetworkCheck {
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
