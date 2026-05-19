package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

public interface LanguageInspectionReporter {
    void report(SemanticDiagnostic diagnostic);
}
