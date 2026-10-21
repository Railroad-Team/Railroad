package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JavaInspectionRuleSettingsTest {

    @Test
    public void persistsRuleSettingsIntoConfigState() {
        JavaInspectionRuleSettings.resetAll();

        JavaInspectionRuleSettings.setRuleEnabled("test:rule", false);
        JavaInspectionRuleSettings.setTagEnabled("imports", false);
        JavaInspectionRuleSettings.setSeverityOverride("test:rule", SemanticDiagnostic.Severity.INFO);

        assertEquals(Boolean.FALSE, ConfigHandler.getConfig().getInspectionRuleEnabledOverrides().get("test:rule"));
        assertEquals(Boolean.FALSE, ConfigHandler.getConfig().getInspectionRuleTagEnabledOverrides().get("imports"));
        assertEquals("INFO", ConfigHandler.getConfig().getInspectionRuleSeverityOverrides().get("test:rule"));
    }

    @Test
    public void reloadsRuleSettingsFromConfigState() {
        ConfigHandler.getConfig().setInspectionRuleEnabledOverrides(Map.of("test:rule", false));
        ConfigHandler.getConfig().setInspectionRuleTagEnabledOverrides(Map.of("names", false));
        ConfigHandler.getConfig().setInspectionRuleSeverityOverrides(Map.of("test:rule", "WARNING"));

        JavaInspectionRuleSettings.reloadFromConfig();

        assertFalse(JavaInspectionRuleSettings.ruleEnabledOverride("test:rule").orElse(true));
        assertFalse(JavaInspectionRuleSettings.tagEnabledOverride("names").orElse(true));
        assertEquals(SemanticDiagnostic.Severity.WARNING,
            JavaInspectionRuleSettings.severityOverride("test:rule").orElseThrow());

        JavaInspectionRuleSettings.resetAll();
        assertTrue(JavaInspectionRuleSettings.ruleEnabledOverrides().isEmpty());
        assertTrue(JavaInspectionRuleSettings.tagEnabledOverrides().isEmpty());
        assertTrue(JavaInspectionRuleSettings.severityOverrides().isEmpty());
    }
}
