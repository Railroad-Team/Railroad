package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LanguageInspectionRuleEngine {
    private LanguageInspectionRuleEngine() {
    }

    public static <C extends LanguageRuleContext> void runRules(LanguageInspectionRuleProvider<C> provider, C context, LanguageInspectionReporter reporter, InspectionSettingsAccess settings) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(settings, "settings");

        for (LanguageInspectionRule<C> rule : provider.rules()) {
            if (rule == null || !settings.isEnabled(rule))
                continue;

            LanguageInspectionRuleReporter ruleReporter =
                new DefaultLanguageInspectionRuleReporter(rule, reporter, settings);

            try {
                rule.evaluate(context, ruleReporter);
            } catch (Exception exception) {
                Railroad.LOGGER.error(
                    "Inspection rule '{}:{}' failed for {}",
                    provider.id(),
                    rule.id(),
                    context.filePath(),
                    exception
                );
            }
        }
    }

    public static <C extends LanguageRuleContext> List<SemanticDiagnostic> collectDiagnostics(
        LanguageInspectionRuleProvider<C> provider,
        C context,
        InspectionSettingsAccess settings
    ) {
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        runRules(provider, context, diagnostics::add, settings);
        return List.copyOf(diagnostics);
    }
}
