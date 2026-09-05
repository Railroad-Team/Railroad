package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of Java inspection rule override state for UI editing.
 *
 * @param ruleEnabledOverrides explicit enablement values keyed by rule identifier
 * @param tagEnabledOverrides enablement values keyed by inspection category
 * @param severityOverrides severity overrides keyed by rule identifier
 */
public record JavaInspectionRuleSettingsState(
    Map<String, Boolean> ruleEnabledOverrides,
    Map<String, Boolean> tagEnabledOverrides,
    Map<String, SemanticDiagnostic.Severity> severityOverrides
) {
    /**
     * Creates a snapshot by copying the supplied override maps.
     *
     * @param ruleEnabledOverrides explicit enablement values keyed by rule identifier
     * @param tagEnabledOverrides enablement values keyed by inspection category
     * @param severityOverrides severity overrides keyed by rule identifier
     */
    public JavaInspectionRuleSettingsState {
        ruleEnabledOverrides = Map.copyOf(Objects.requireNonNull(ruleEnabledOverrides, "ruleEnabledOverrides"));
        tagEnabledOverrides = Map.copyOf(Objects.requireNonNull(tagEnabledOverrides, "tagEnabledOverrides"));
        severityOverrides = Map.copyOf(Objects.requireNonNull(severityOverrides, "severityOverrides"));
    }

    /**
     * Creates a settings snapshot without overrides.
     *
     * @return empty settings snapshot
     */
    public static JavaInspectionRuleSettingsState empty() {
        return new JavaInspectionRuleSettingsState(Map.of(), Map.of(), Map.of());
    }

    /**
     * Captures the current inspection overrides.
     *
     * @return immutable settings snapshot
     */
    public static JavaInspectionRuleSettingsState snapshot() {
        return new JavaInspectionRuleSettingsState(
            new LinkedHashMap<>(JavaInspectionRuleSettings.ruleEnabledOverrides()),
            new LinkedHashMap<>(JavaInspectionRuleSettings.tagEnabledOverrides()),
            new LinkedHashMap<>(JavaInspectionRuleSettings.severityOverrides()));
    }
}
