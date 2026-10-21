package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

/**
 * Provides the enabled state and effective severity of inspection rules.
 */
public interface InspectionSettingsAccess {
    /**
     * Checks whether a rule should run under the current settings.
     *
     * @param rule rule whose enabled state is requested
     * @return {@code true} if the rule is enabled
     */
    boolean isEnabled(LanguageInspectionRule<?> rule);

    /**
     * Resolves the severity to use for a rule's diagnostics, including any configured override.
     *
     * @param rule rule whose severity is requested
     * @return the configured severity, or the rule's default when no override applies
     */
    SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule);
}
