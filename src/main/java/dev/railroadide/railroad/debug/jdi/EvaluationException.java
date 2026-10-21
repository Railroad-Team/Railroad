package dev.railroadide.railroad.debug.jdi;

/**
 * Reports an unsupported or unresolved debugger watch expression.
 */
public final class EvaluationException extends Exception {
    /**
     * Creates an evaluation failure with a message suitable for an inline watch error.
     *
     * @param message description of the evaluation failure
     */
    public EvaluationException(String message) {
        super(message);
    }
}
