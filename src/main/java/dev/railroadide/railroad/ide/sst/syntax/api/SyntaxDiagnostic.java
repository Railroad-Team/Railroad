package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.Objects;

/**
 * Syntax-level diagnostic emitted by the green/red parser pipeline.
 *
 * @param severity the diagnostic severity
 * @param code the nonblank diagnostic identifier
 * @param message the nonblank diagnostic message
 * @param startOffset the inclusive nonnegative UTF-16 source offset
 * @param endOffset the exclusive UTF-16 source offset, at least the start offset
 */
public record SyntaxDiagnostic(
    Severity severity,
    String code,
    String message,
    int startOffset,
    int endOffset
) {
    /**
     * Creates a diagnostic with nonblank code and message and a valid source range.
     *
     * @param severity the diagnostic severity
     * @param code the nonblank diagnostic identifier
     * @param message the nonblank diagnostic message
     * @param startOffset the inclusive nonnegative UTF-16 source offset
     * @param endOffset the exclusive UTF-16 source offset, at least the start offset
     */
    public SyntaxDiagnostic {
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        if (code.isBlank())
            throw new IllegalArgumentException("code cannot be blank");
        if (message.isBlank())
            throw new IllegalArgumentException("message cannot be blank");
        if (startOffset < 0)
            throw new IllegalArgumentException("startOffset cannot be negative");
        if (endOffset < startOffset)
            throw new IllegalArgumentException("endOffset cannot be less than startOffset");
    }

    /**
     * Severity categories for syntax diagnostics displayed by the editor.
     */
    public enum Severity {
        /**
         * An error that prevents the source from being valid.
         */
        ERROR,
        /**
         * A potential problem that does not necessarily invalidate the source.
         */
        WARNING,
        /**
         * An informational diagnostic.
         */
        INFO
    }
}
