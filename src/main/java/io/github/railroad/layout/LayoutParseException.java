package io.github.railroad.layout;

import java.io.IOException;

public class LayoutParseException extends IOException {
    public LayoutParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public LayoutParseException(String message) {
        super(message);
    }

    public LayoutParseException(Throwable cause) {
        super(cause);
    }
}
