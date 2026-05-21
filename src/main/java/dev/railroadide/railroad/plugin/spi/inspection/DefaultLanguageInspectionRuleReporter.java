package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.Objects;

final class DefaultLanguageInspectionRuleReporter<C extends LanguageRuleContext> implements LanguageInspectionRuleReporter {
    private final LanguageInspectionRule<C> rule;
    private final LanguageInspectionReporter sink;
    private final InspectionSettingsAccess settings;

    DefaultLanguageInspectionRuleReporter(
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
                exception
            );
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
            node
        ));
    }
}
