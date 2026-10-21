package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.Set;

/**
 * Defines one language inspection with diagnostic metadata and evaluation logic.
 *
 * @param <C> language context required to evaluate the rule
 */
public interface LanguageInspectionRule<C extends LanguageRuleContext> {
    /**
     * Returns the stable identifier used for settings and diagnostic codes.
     *
     * @return the rule ID, conventionally namespaced by its contributing plugin
     */
    String id();

    /**
     * Returns the severity used when settings do not override it.
     *
     * @return default severity for this rule's diagnostics
     */
    SemanticDiagnostic.Severity defaultSeverity();

    /**
     * Returns the format string used by {@link LanguageInspectionRuleReporter#report}.
     *
     * @return diagnostic message template compatible with {@link String#format(String, Object...)}
     */
    String messageTemplate();

    /**
     * Returns optional categories describing this rule.
     *
     * @return rule tags, empty by default
     */
    default Set<String> tags() {
        return Set.of();
    }

    /**
     * Inspects one document snapshot and reports any findings.
     * Implementations should treat the context as read-only.
     *
     * @param context language-specific context for the document being inspected
     * @param reporter reporter bound to this rule's metadata
     */
    void evaluate(C context, LanguageInspectionRuleReporter reporter);
}
