package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.config.Config;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config-backed controls for Java inspection rules.
 */
public final class JavaInspectionRuleSettings {
    private static final Map<String, Boolean> RULE_ENABLED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> TAG_ENABLED = new ConcurrentHashMap<>();
    private static final Map<String, SemanticDiagnostic.Severity> RULE_SEVERITY_OVERRIDES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private JavaInspectionRuleSettings() {
    }

    /**
     * Resolves rule enablement, giving a rule override precedence over disabled tags.
     *
     * @param rule rule whose behavior is queried
     * @return whether the rule is enabled
     */
    public static boolean isEnabled(JavaInspectionRule rule) {
        Objects.requireNonNull(rule, "rule");
        ensureLoaded();

        Boolean explicit = RULE_ENABLED.get(rule.id());
        if (explicit != null)
            return explicit;

        for (String tag : rule.tags()) {
            Boolean tagEnabled = TAG_ENABLED.get(tag);
            if (tagEnabled != null && !tagEnabled)
                return false;
        }

        return true;
    }

    /**
     * Resolves the severity override or falls back to the rule default.
     *
     * @param rule rule whose behavior is queried
     * @return severity to use for this rule
     */
    public static SemanticDiagnostic.Severity effectiveSeverity(JavaInspectionRule rule) {
        Objects.requireNonNull(rule, "rule");
        ensureLoaded();
        return RULE_SEVERITY_OVERRIDES.getOrDefault(rule.id(), rule.defaultSeverity());
    }

    /**
     * Looks up the explicit enablement override for a rule.
     *
     * @param ruleId identifier of the rule to configure
     * @return override, or empty if the rule uses tag defaults
     */
    public static Optional<Boolean> ruleEnabledOverride(String ruleId) {
        ensureLoaded();
        return Optional.ofNullable(RULE_ENABLED.get(Objects.requireNonNull(ruleId, "ruleId")));
    }

    /**
     * Looks up the enablement override for an inspection category.
     *
     * @param tag inspection category to configure
     * @return override, or empty if the category uses defaults
     */
    public static Optional<Boolean> tagEnabledOverride(String tag) {
        ensureLoaded();
        return Optional.ofNullable(TAG_ENABLED.get(Objects.requireNonNull(tag, "tag")));
    }

    /**
     * Looks up the explicit diagnostic severity for a rule.
     *
     * @param ruleId identifier of the rule to configure
     * @return override, or empty if the default severity applies
     */
    public static Optional<SemanticDiagnostic.Severity> severityOverride(String ruleId) {
        ensureLoaded();
        return Optional.ofNullable(RULE_SEVERITY_OVERRIDES.get(Objects.requireNonNull(ruleId, "ruleId")));
    }

    /**
     * Copies the current rule enablement overrides.
     *
     * @return immutable snapshot keyed by rule identifier
     */
    public static Map<String, Boolean> ruleEnabledOverrides() {
        ensureLoaded();
        return Map.copyOf(RULE_ENABLED);
    }

    /**
     * Copies the current category enablement overrides.
     *
     * @return immutable snapshot keyed by category
     */
    public static Map<String, Boolean> tagEnabledOverrides() {
        ensureLoaded();
        return Map.copyOf(TAG_ENABLED);
    }

    /**
     * Copies the current severity overrides.
     *
     * @return immutable snapshot keyed by rule identifier
     */
    public static Map<String, SemanticDiagnostic.Severity> severityOverrides() {
        ensureLoaded();
        return Map.copyOf(RULE_SEVERITY_OVERRIDES);
    }

    /**
     * Sets a rule enablement override and updates the configuration.
     *
     * @param ruleId identifier of the rule to configure
     * @param enabled explicit enablement override
     */
    public static void setRuleEnabled(String ruleId, boolean enabled) {
        ensureLoaded();
        RULE_ENABLED.put(Objects.requireNonNull(ruleId, "ruleId"), enabled);
        persistToConfig();
    }

    /**
     * Removes a rule enablement override and updates the configuration.
     *
     * @param ruleId identifier of the rule to configure
     */
    public static void clearRuleEnabled(String ruleId) {
        ensureLoaded();
        RULE_ENABLED.remove(Objects.requireNonNull(ruleId, "ruleId"));
        persistToConfig();
    }

    /**
     * Sets a category enablement override and updates the configuration.
     *
     * @param tag inspection category to configure
     * @param enabled explicit enablement override
     */
    public static void setTagEnabled(String tag, boolean enabled) {
        ensureLoaded();
        TAG_ENABLED.put(Objects.requireNonNull(tag, "tag"), enabled);
        persistToConfig();
    }

    /**
     * Removes a category enablement override and updates the configuration.
     *
     * @param tag inspection category to configure
     */
    public static void clearTagEnabled(String tag) {
        ensureLoaded();
        TAG_ENABLED.remove(Objects.requireNonNull(tag, "tag"));
        persistToConfig();
    }

    /**
     * Sets a rule severity override and updates the configuration.
     *
     * @param ruleId identifier of the rule to configure
     * @param severity default diagnostic severity
     */
    public static void setSeverityOverride(String ruleId, SemanticDiagnostic.Severity severity) {
        ensureLoaded();
        RULE_SEVERITY_OVERRIDES.put(Objects.requireNonNull(ruleId, "ruleId"),
            Objects.requireNonNull(severity, "severity"));
        persistToConfig();
    }

    /**
     * Removes a severity override and updates the configuration.
     *
     * @param ruleId identifier of the rule to configure
     */
    public static void clearSeverityOverride(String ruleId) {
        ensureLoaded();
        RULE_SEVERITY_OVERRIDES.remove(Objects.requireNonNull(ruleId, "ruleId"));
        persistToConfig();
    }

    /**
     * Reloads all overrides from configuration, ignoring unrecognized severity names.
     */
    public static void reloadFromConfig() {
        Config config = ConfigHandler.getConfig();
        RULE_ENABLED.clear();
        TAG_ENABLED.clear();
        RULE_SEVERITY_OVERRIDES.clear();
        RULE_ENABLED.putAll(config.getInspectionRuleEnabledOverrides());
        TAG_ENABLED.putAll(config.getInspectionRuleTagEnabledOverrides());
        config.getInspectionRuleSeverityOverrides().forEach((ruleId, severityName) -> {
            if (severityName == null || severityName.isBlank())
                return;
            try {
                RULE_SEVERITY_OVERRIDES.put(ruleId, SemanticDiagnostic.Severity.valueOf(severityName));
            } catch (IllegalArgumentException _) {
            }
        });
        loaded = true;
    }

    /**
     * Replaces all overrides with a snapshot and updates the configuration.
     *
     * @param state inspection overrides to apply
     */
    public static void replaceAll(JavaInspectionRuleSettingsState state) {
        Objects.requireNonNull(state, "state");
        RULE_ENABLED.clear();
        TAG_ENABLED.clear();
        RULE_SEVERITY_OVERRIDES.clear();
        RULE_ENABLED.putAll(state.ruleEnabledOverrides());
        TAG_ENABLED.putAll(state.tagEnabledOverrides());
        RULE_SEVERITY_OVERRIDES.putAll(state.severityOverrides());
        loaded = true;
        persistToConfig();
    }

    /**
     * Clears all overrides and restores the default rule settings.
     */
    public static void resetAll() {
        replaceAll(JavaInspectionRuleSettingsState.empty());
    }

    private static void ensureLoaded() {
        if (loaded)
            return;
        synchronized (JavaInspectionRuleSettings.class) {
            if (!loaded) {
                reloadFromConfig();
            }
        }
    }

    private static void persistToConfig() {
        Config config = ConfigHandler.getConfig();
        config.setInspectionRuleEnabledOverrides(new LinkedHashMap<>(RULE_ENABLED));
        config.setInspectionRuleTagEnabledOverrides(new LinkedHashMap<>(TAG_ENABLED));

        Map<String, String> severityNames = new LinkedHashMap<>();
        RULE_SEVERITY_OVERRIDES.forEach((ruleId, severity) -> severityNames.put(ruleId, severity.name()));
        config.setInspectionRuleSeverityOverrides(severityNames);
    }
}
