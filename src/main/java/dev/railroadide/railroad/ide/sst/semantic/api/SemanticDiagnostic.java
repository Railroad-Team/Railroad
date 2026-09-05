package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Semantic-level diagnostic emitted by name resolution, type analysis, or inspections.
 *
 * @param severity the diagnostic severity
 * @param code the nonblank diagnostic identifier
 * @param message the nonblank diagnostic message
 * @param startOffset the inclusive nonnegative UTF-16 source offset
 * @param endOffset the exclusive UTF-16 source offset, at least the start offset
 * @param node the related syntax node, or {@code null} when unavailable
 */
public record SemanticDiagnostic(
    Severity severity,
    String code,
    String message,
    int startOffset,
    int endOffset,
    @Nullable SyntaxNode node
) {
    /**
     * Creates a diagnostic with nonblank code and message and a valid source range.
     *
     * @param severity the diagnostic severity
     * @param code the nonblank diagnostic identifier
     * @param message the nonblank diagnostic message
     * @param startOffset the inclusive nonnegative UTF-16 source offset
     * @param endOffset the exclusive UTF-16 source offset, at least the start offset
     * @param node the related syntax node, or {@code null} when unavailable
     */
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
     *
     * @return the attached syntax node, or an empty optional
     */
    public Optional<SyntaxNode> nodeOptional() {
        return Optional.ofNullable(node);
    }

    /**
     * Diagnostic severity used by the editor and inspection pipeline.
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
