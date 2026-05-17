package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes Java inspection rules and applies rule settings.
 */
public final class JavaInspectionRuleEngine {
    private JavaInspectionRuleEngine() {
    }

    public static void runRules(
            JavaInspectionRuleProvider provider,
            JavaRuleContext context,
            JavaInspectionReporter reporter
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(reporter, "reporter");

        List<JavaInspectionRule> rules = provider.rules();
        if (rules == null || rules.isEmpty())
            return;

        for (JavaInspectionRule rule : rules) {
            if (rule == null)
                continue;
            if (!JavaInspectionRuleSettings.isEnabled(rule))
                continue;

            try {
                JavaInspectionRuleReporter ruleReporter = new RuleReporter(rule, reporter);
                rule.evaluate(context, ruleReporter);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Java inspection rule '{}:{}' failed for {}",
                        provider.id(), rule.id(), context.filePath(), exception);
            }
        }
    }

    public static List<SemanticDiagnostic> collectDiagnostics(
            JavaInspectionRuleProvider provider,
            JavaRuleContext context
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(context, "context");

        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        runRules(provider, context, diagnostics::add);
        return List.copyOf(diagnostics);
    }

    private record RuleReporter(JavaInspectionRule rule, JavaInspectionReporter sink) implements JavaInspectionRuleReporter {
        @Override
        public void report(SyntaxNode node, Object... messageArgs) {
            String message;
            try {
                message = String.format(rule.messageTemplate(), messageArgs);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to format message for Java inspection rule '{}:{}' with args {}: {}",
                        rule.id(), rule.messageTemplate(), messageArgs, exception);
                message = rule.messageTemplate();
            }
            reportMessage(node, message);
        }

        @Override
        public void reportMessage(SyntaxNode node, String message) {
            sink.report(new SemanticDiagnostic(
                    JavaInspectionRuleSettings.effectiveSeverity(rule),
                    rule.id(),
                    message,
                    node.start(),
                    node.end(),
                    node
            ));
        }
    }
}
