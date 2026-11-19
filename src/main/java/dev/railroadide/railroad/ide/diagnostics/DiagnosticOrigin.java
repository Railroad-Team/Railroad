package dev.railroadide.railroad.ide.diagnostics;

/** The stage that produced a diagnostic, independently of its provider or diagnostic code. */
public enum DiagnosticOrigin {
    /**
     * Reports a problem detected while parsing document syntax.
     */
    SYNTAX,
    /**
     * Reports a problem detected by semantic analysis.
     */
    SEMANTIC,
    /**
     * Reports a problem emitted by a build tool.
     */
    BUILD,
    /**
     * Reports a finding from a code inspection.
     */
    INSPECTION
}
