package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import java.util.Set;

/**
 * A single Java inspection rule with metadata and evaluation logic.
 * <p>
 * This is the preferred extension point for new Java inspections. A provider can expose
 * many rules, each with its own id, severity, tags, message template, and evaluation
 * logic.
 * <p>
 * Rules usually inspect the syntax tree through {@link JavaRuleContext#syntaxTree()} or
 * {@link JavaRuleContext#traverse(java.util.function.Consumer)}, then combine that with
 * semantic queries such as {@link JavaRuleContext#resolvedSymbol} and
 * {@link JavaRuleContext#inferredType}.
 */
public interface JavaInspectionRule extends LanguageInspectionRule<JavaRuleContext> {
    /**
     * Stable namespaced id for this rule (e.g. {@code my.plugin:my-rule}).
     *
     * @return stable rule id
     */
    String id();

    /**
     * Default diagnostic severity for reports produced by this rule.
     *
     * @return default severity for emitted diagnostics
     */
    SemanticDiagnostic.Severity defaultSeverity();

    /**
     * Message template used by {@link JavaInspectionRuleReporter#report}.
     *
     * @return default message template
     */
    String messageTemplate();

    /**
     * Optional tags/categories (e.g. {@code imports}, {@code types}).
     *
     * @return immutable tag set
     */
    default Set<String> tags() {
        return Set.of();
    }

    /**
     * Evaluates the rule and emits diagnostics through the provided reporter.
     * <p>
     * Implementations should be side-effect-free and treat the supplied context as an
     * immutable snapshot of one file.
     *
     * @param context immutable inspection context for the current file
     * @param reporter diagnostic reporter for this rule
     * @throws NullPointerException if an implementation does not tolerate {@code null}
     */
    void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter);

    @Override
    default void evaluate(JavaRuleContext context, LanguageInspectionRuleReporter reporter) {
        if (reporter instanceof JavaInspectionRuleReporter javaReporter) {
            evaluate(context, javaReporter);
            return;
        }

        evaluate(context, new JavaInspectionRuleReporter() {
            @Override
            public void report(SyntaxNode node, Object... messageArgs) {
                reporter.report(node, messageArgs);
            }

            @Override
            public void reportMessage(SyntaxNode node, String message) {
                reporter.reportMessage(node, message);
            }
        });
    }
}
