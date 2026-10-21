package dev.railroadide.railroad.window;

/**
 * Alert severities controlling the icon, styling, and default button labels.
 */
public enum AlertType {
    /** General information requiring acknowledgment. */
    INFO,
    /** Confirmation that an operation completed successfully. */
    SUCCESS,
    /** A warning with an option to proceed. */
    WARNING,
    /** An error requiring acknowledgment or dismissal. */
    ERROR
}
