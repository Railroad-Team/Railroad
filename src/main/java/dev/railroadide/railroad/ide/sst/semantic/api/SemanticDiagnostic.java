package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Semantic-level diagnostic emitted by name resolution, type analysis, or inspections.
 */
public record SemanticDiagnostic(
        Severity severity,
        String code,
        String message,
        int startOffset,
        int endOffset,
        @Nullable SyntaxNode node
) {
    public SemanticDiagnostic {
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
     * Returns the attached syntax node when one was provided.
     */
    public Optional<SyntaxNode> nodeOptional() {
        return Optional.ofNullable(node);
    }

    /**
     * Diagnostic severity used by the editor and inspection pipeline.
     */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }
}
