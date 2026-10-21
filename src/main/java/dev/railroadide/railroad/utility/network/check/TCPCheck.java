package dev.railroadide.railroad.utility.network.check;

import dev.railroadide.railroad.Railroad;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Tests whether a host accepts TCP connections on port 53; no DNS request is sent. */
public class TCPCheck implements NetworkCheck {
    /** Creates a TCP port-53 probe with failure logging enabled. */
    public TCPCheck() {
    }

    /**
     * Resolves the target and attempts to open a TCP connection to port 53, closing the socket afterward.
     * Exceptions produce false and are logged when {@link #shouldLogFailures()} is true.
     * Host resolution precedes the timed connection attempt.
     *
     * @param address hostname or IP address, without a scheme or port suffix
     * @param timeout connection timeout in milliseconds; zero waits indefinitely and negative values fail the probe
     * @return true if the connection and socket cleanup succeed, or false if an exception occurs
     */
    @Override
    public boolean check(String address, int timeout) {
        try (var socket = new Socket()) {
            var socketAddress = new InetSocketAddress(address, 53);
            socket.connect(socketAddress, timeout);
            return true;
        } catch (Exception exception) {
            if (shouldLogFailures()) {
                Railroad.LOGGER.error("An error occurred while trying to connect via DNS over TCP.", exception);
            }
            return false;
        }
    }
}
