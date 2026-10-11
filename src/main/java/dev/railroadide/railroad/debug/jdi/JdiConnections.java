package dev.railroadide.railroad.debug.jdi;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Connects to a socket-based JDI target with bounded retries and optional cancellation.
 */
public final class JdiConnections {
    private static final long ATTACH_RETRY_TIMEOUT_MILLIS = 5_000;
    private static final long ATTACH_RETRY_DELAY_MILLIS = 50;

    private JdiConnections() {
    }

    /**
     * Attaches through the JDI socket connector, retrying refused connections for up to five seconds.
     *
     * @param host debug target hostname or address
     * @param port debug target socket port
     * @return the attached virtual machine
     * @throws IllegalConnectorArgumentsException if socket connector arguments are invalid
     * @throws IOException if connecting fails or the retry wait is interrupted
     */
    public static VirtualMachine attach(String host, int port) throws IllegalConnectorArgumentsException, IOException {
        return attach(host, port, Duration.ofMillis(ATTACH_RETRY_TIMEOUT_MILLIS), () -> false);
    }

    /**
     * Retries a socket attachment until the target appears, the deadline expires, or its owner cancels.
     *
     * @param host debug target host
     * @param port debug target port
     * @param timeout maximum retry duration
     * @param cancelled whether the owning execution has ended
     * @return attached virtual machine
     * @throws IllegalConnectorArgumentsException if connector arguments are invalid
     * @throws IOException if the connection fails
     */
    public static VirtualMachine attach(String host, int port, Duration timeout, BooleanSupplier cancelled)
        throws IllegalConnectorArgumentsException, IOException {
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Attachment timeout must be positive");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException(String.format("Invalid port number: %d", port));

        AttachingConnector connector = Bootstrap.virtualMachineManager()
            .attachingConnectors()
            .stream()
            .filter(candidate -> candidate.name().equals("com.sun.jdi.SocketAttach"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not find attaching connector"));

        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(host);
        arguments.get("port").setValue(Integer.toString(port));
        arguments.get("timeout").setValue("250");

        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (cancelled.getAsBoolean())
                throw new CancellationException("Debug attachment cancelled");
            try {
                VirtualMachine vm = connector.attach(arguments);
                if (cancelled.getAsBoolean()) {
                    vm.dispose();
                    throw new CancellationException("Debug attachment cancelled");
                }
                return vm;
            } catch (ConnectException exception) {
                if (System.nanoTime() >= deadline)
                    throw exception;

                try {
                    Thread.sleep(ATTACH_RETRY_DELAY_MILLIS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for the debug target", interruptedException);
                }
            }
        }
    }
}
