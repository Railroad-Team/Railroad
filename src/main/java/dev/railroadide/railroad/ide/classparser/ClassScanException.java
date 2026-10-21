package dev.railroadide.railroad.ide.classparser;

/**
 * Signals a failure to read class metadata while scanning bytecode.
 */
public class ClassScanException extends RuntimeException {
    /**
     * Creates a class scanning failure with the supplied diagnostic details.
     *
     * @param message the diagnostic message
     */
    public ClassScanException(String message) {
        super(message);
    }

    /**
     * Creates a class scanning failure with the supplied diagnostic details.
     *
     * @param message the diagnostic message
     * @param cause the underlying failure
     */
    public ClassScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
