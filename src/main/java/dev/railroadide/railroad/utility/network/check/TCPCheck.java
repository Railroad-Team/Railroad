package dev.railroadide.railroad.utility.network.check;

import dev.railroadide.railroad.Railroad;

import java.net.InetSocketAddress;
import java.net.Socket;

public class TCPCheck implements NetworkCheck {
    @Override
    public boolean check(String address, int timeout) {
        try (var socket = new Socket()) {
            var socketAddress = new InetSocketAddress(address, 53);
            socket.connect(socketAddress, timeout);
            return true;
        } catch (Exception exception) {
            if (shouldLogFailures())
                Railroad.LOGGER.error("An error occurred while trying to connect via DNS over TCP.", exception);
            return false;
        }
    }
}
