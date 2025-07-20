package io.github.railroad.core.form;

/**
 * Represents the result of a validation.
 *
 * @param status  the status of the validation
 *                (OK, WARNING, ERROR)
 * @param message the message of the validation
 */
public record ValidationResult(Status status, String message) {
    /**
     * Creates a new ValidationResult with the status OK and no message.
     *
     * @return a new ValidationResult
     */
    public static ValidationResult ok() {
        return ok(null);
    }

    /**
     * Creates a new ValidationResult with the status OK and the given message.
     *
     * @param message the message of the validation
     *                (can be null)
     * @return a new ValidationResult
     */
    public static ValidationResult ok(String message) {
        return new ValidationResult(Status.OK, message);
    }

    /**
     * Creates a new ValidationResult with the status WARNING and the given message.
     *
     * @param message the message of the validation
     *                (can be null)
     * @return a new ValidationResult
     */
    public static ValidationResult warning(String message) {
        return new ValidationResult(Status.WARNING, message);
    }

    /**
     * Creates a new ValidationResult with the status ERROR and the given message.
     *
     * @param message the message of the validation
     *                (can be null)
     * @return a new ValidationResult
     */
    public static ValidationResult error(String message) {
        return new ValidationResult(Status.ERROR, message);
    }

    /**
     * Represents the status of the validation.
     *
     * <p>It can be OK, WARNING or ERROR.
     */
    public enum Status {
        OK, WARNING, ERROR
    }
}
