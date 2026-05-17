package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

/**
 * Sink used by inspections to report diagnostics.
 * <p>
 * This is the low-level diagnostic sink used by the rule engine. New rule-based inspections
 * normally report through {@link JavaInspectionRuleReporter} instead of constructing
 * {@link SemanticDiagnostic} instances directly.
 */
public interface JavaInspectionReporter {
    /**
     * Reports a fully constructed diagnostic.
     *
     * @param diagnostic diagnostic to emit
     * @throws NullPointerException if {@code diagnostic} is {@code null}
     */
    void report(SemanticDiagnostic diagnostic);

    /**
     * Reports an error covering the supplied syntax node.
     *
     * @param code stable diagnostic code
     * @param message diagnostic message
     * @param node highlighted syntax node
     * @throws NullPointerException if any argument is {@code null}
     */
    default void error(String code, String message, SyntaxNode node) {
        report(new SemanticDiagnostic(
                SemanticDiagnostic.Severity.ERROR,
                code,
                message,
                node.start(),
                node.end(),
                node
        ));
    }

    /**
     * Reports a warning covering the supplied syntax node.
     *
     * @param code stable diagnostic code
     * @param message diagnostic message
     * @param node highlighted syntax node
     * @throws NullPointerException if any argument is {@code null}
     */
    default void warning(String code, String message, SyntaxNode node) {
        report(new SemanticDiagnostic(
                SemanticDiagnostic.Severity.WARNING,
                code,
                message,
                node.start(),
                node.end(),
                node
        ));
    }

    /**
     * Reports an informational diagnostic covering the supplied syntax node.
     *
     * @param code stable diagnostic code
     * @param message diagnostic message
     * @param node highlighted syntax node
     * @throws NullPointerException if any argument is {@code null}
     */
    default void info(String code, String message, SyntaxNode node) {
        report(new SemanticDiagnostic(
                SemanticDiagnostic.Severity.INFO,
                code,
                message,
                node.start(),
                node.end(),
                node
        ));
    }
}
