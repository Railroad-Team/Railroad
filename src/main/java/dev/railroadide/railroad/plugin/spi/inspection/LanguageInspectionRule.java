package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

import java.util.Set;

public interface LanguageInspectionRule<C extends LanguageRuleContext> {
    String id();

    SemanticDiagnostic.Severity defaultSeverity();

    String messageTemplate();

    default Set<String> tags() {
        return Set.of();
    }

    void evaluate(C context, LanguageInspectionRuleReporter reporter);
}
