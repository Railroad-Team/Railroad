package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.*;

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

        LanguageInspectionRuleEngine.runRules(provider, context, reporter, JAVA_SETTINGS_ACCESS);
    }

    public static List<SemanticDiagnostic> collectDiagnostics(
            JavaInspectionRuleProvider provider,
            JavaRuleContext context
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(context, "context");

        return LanguageInspectionRuleEngine.collectDiagnostics(provider, context, JAVA_SETTINGS_ACCESS);
    }

    private static final InspectionSettingsAccess JAVA_SETTINGS_ACCESS = new InspectionSettingsAccess() {
        @Override
        public boolean isEnabled(LanguageInspectionRule<?> rule) {
            return JavaInspectionRuleSettings.isEnabled(asJavaRule(rule));
        }

        @Override
        public SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule) {
            return JavaInspectionRuleSettings.effectiveSeverity(asJavaRule(rule));
        }
    };

    private static JavaInspectionRule asJavaRule(LanguageInspectionRule<?> rule) {
        if (rule instanceof JavaInspectionRule javaRule)
            return javaRule;

        throw new IllegalStateException("Inspection rule '" + rule.id() + "' is not a Java rule.");
    }
}
