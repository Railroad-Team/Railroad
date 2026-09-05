package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.Objects;

/**
 * Converts rule reports into diagnostics using the rule ID and configured severity.
 * Messages are formatted with {@link String#format(String, Object...)}; formatting failures
 * are logged and fall back to the rule's unformatted template.
 *
 * @param <C> context type consumed by the inspection rule
 */
public final class DefaultLanguageInspectionRuleReporter<C extends LanguageRuleContext>
    implements
        LanguageInspectionRuleReporter {
    private final LanguageInspectionRule<C> rule;
    private final LanguageInspectionReporter sink;
    private final InspectionSettingsAccess settings;

    /**
     * Creates a reporter bound to one inspection rule.
     *
     * @param rule rule supplying diagnostic metadata and the message template
     * @param sink destination for constructed diagnostics
     * @param settings settings used to resolve diagnostic severity
     * @throws NullPointerException if any argument is null
     */
    public DefaultLanguageInspectionRuleReporter(
        LanguageInspectionRule<C> rule,
        LanguageInspectionReporter sink,
        InspectionSettingsAccess settings
    ) {
        this.rule = Objects.requireNonNull(rule, "rule");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public void report(SyntaxNode node, Object... messageArgs) {
        String message;
        try {
            message = String.format(rule.messageTemplate(), messageArgs);
        } catch (Exception exception) {
            Railroad.LOGGER.error(
                "Failed to format message for inspection rule '{}:{}' with args {}",
                rule.id(),
                rule.messageTemplate(),
                messageArgs,
                exception);
            message = rule.messageTemplate();
        }

        reportMessage(node, message);
    }

    @Override
    public void reportMessage(SyntaxNode node, String message) {
        sink.report(new SemanticDiagnostic(
            settings.effectiveSeverity(rule),
            rule.id(),
            message,
            node.start(),
            node.end(),
            node));
    }
}
