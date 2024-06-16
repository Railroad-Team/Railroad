package io.github.railroad.ui.form;

public record ValidationResult(ValidationResult.Status status, String message) {
    public static ValidationResult ok() {
        return ok(null);
    }

    public static ValidationResult warning(String message) {
        return new ValidationResult(Status.WARNING, message);
    }

    public static ValidationResult error(String message) {
        return new ValidationResult(Status.ERROR, message);
    }

    public static ValidationResult ok(String message) {
        return new ValidationResult(Status.OK, message);
    }

    public enum Status {
        OK, WARNING, ERROR
    }
}
