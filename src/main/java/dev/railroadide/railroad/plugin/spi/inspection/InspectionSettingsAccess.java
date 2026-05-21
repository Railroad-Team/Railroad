package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

public interface InspectionSettingsAccess {
    boolean isEnabled(LanguageInspectionRule<?> rule);

    SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule);
}
