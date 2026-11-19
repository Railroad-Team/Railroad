package dev.railroadide.railroad.ide.diagnostics;

/** Language-neutral diagnostic severity, ordered from most to least severe. */
public enum DiagnosticSeverity {
    /**
     * Indicates an error requiring correction.
     */
    ERROR,
    /**
     * Indicates a potential problem that merits attention.
     */
    WARNING,
    /**
     * Provides informational feedback about the document.
     */
    INFORMATION,
    /**
     * Provides a low-priority suggestion for improving the document.
     */
    HINT
}
