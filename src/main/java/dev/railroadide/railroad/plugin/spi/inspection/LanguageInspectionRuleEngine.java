package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates enabled inspection rules and routes diagnostics through configured severity settings.
 */
public final class LanguageInspectionRuleEngine {
    private LanguageInspectionRuleEngine() {
    }

    /**
     * Runs a provider's enabled, nonnull rules in list order.
     * Exceptions from individual rule evaluations are logged so subsequent rules can run.
     *
     * @param <C> context type accepted by the provider's rules
     * @param provider provider supplying the rules to evaluate
     * @param context document snapshot to inspect
     * @param reporter destination for emitted diagnostics
     * @param settings enabled states and severity overrides for the rules
     * @throws NullPointerException if any argument is null
     */
    public static <C extends LanguageRuleContext> void runRules(
        LanguageInspectionRuleProvider<C> provider,
        C context,
        LanguageInspectionReporter reporter,
        InspectionSettingsAccess settings
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(settings, "settings");

        for (LanguageInspectionRule<C> rule : provider.rules()) {
            if (rule == null || !settings.isEnabled(rule))
                continue;

            LanguageInspectionRuleReporter ruleReporter = new DefaultLanguageInspectionRuleReporter(rule, reporter,
                settings);

            try {
                rule.evaluate(context, ruleReporter);
            } catch (Exception exception) {
                Railroad.LOGGER.error(
                    "Inspection rule '{}:{}' failed for {}",
                    provider.id(),
                    rule.id(),
                    context.filePath(),
                    exception);
            }
        }
    }

    /**
     * Runs enabled rules and collects their diagnostics in emission order.
     * Evaluation failures are handled as in {@link #runRules}.
     *
     * @param <C> context type accepted by the provider's rules
     * @param provider provider supplying the rules to evaluate
     * @param context document snapshot to inspect
     * @param settings enabled states and severity overrides for the rules
     * @return an immutable list of collected diagnostics
     * @throws NullPointerException if any argument is null
     */
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
