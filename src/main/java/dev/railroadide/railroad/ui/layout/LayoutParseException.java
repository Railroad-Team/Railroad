package dev.railroadide.railroad.ui.layout;

import java.io.IOException;

/** Indicates that a layout could not be read or its contents could not be parsed. */
public class LayoutParseException extends IOException {
    /**
     * Creates a failure with a diagnostic message and underlying cause.
     *
     * @param message explanation of the failure
     * @param cause underlying read or parse failure
     */
    public LayoutParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message explanation of the failure
     */
    public LayoutParseException(String message) {
        super(message);
    }

    /**
     * Creates a failure whose message is derived from its cause.
     *
     * @param cause underlying read or parse failure
     */
    public LayoutParseException(Throwable cause) {
        super(cause);
    }
}
