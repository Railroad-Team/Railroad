package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

/**
 * Receives fully constructed diagnostics from language inspections.
 */
public interface LanguageInspectionReporter {
    /**
     * Emits a diagnostic to the inspection result consumer.
     *
     * @param diagnostic diagnostic containing severity, message, and source location
     */
    void report(SemanticDiagnostic diagnostic);
}
