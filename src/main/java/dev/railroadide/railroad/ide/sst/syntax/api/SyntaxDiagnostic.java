package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.Objects;

/**
 * Syntax-level diagnostic emitted by the green/red parser pipeline.
 */
public record SyntaxDiagnostic(
        Severity severity,
        String code,
        String message,
        int startOffset,
        int endOffset
) {
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

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }
}
