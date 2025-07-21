package dev.railroadide.railroad.ide.classparser;

public class ClassScanException extends RuntimeException {
    public ClassScanException(String message) {
        super(message);
    }

    public ClassScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
