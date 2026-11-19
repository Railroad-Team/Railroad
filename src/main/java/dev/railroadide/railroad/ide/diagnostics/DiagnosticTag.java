package dev.railroadide.railroad.ide.diagnostics;

/** Additional presentation hints that do not change a diagnostic's severity. */
public enum DiagnosticTag {
    /**
     * Marks code or content that is unnecessary, such as an unused declaration.
     */
    UNNECESSARY,
    /**
     * Marks use of a deprecated construct.
     */
    DEPRECATED
}
