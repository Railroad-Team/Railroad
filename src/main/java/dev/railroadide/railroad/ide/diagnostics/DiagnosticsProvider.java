package dev.railroadide.railroad.ide.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides language diagnostics for a document snapshot.
 */
public interface DiagnosticsProvider {
    /**
     * Computes diagnostics for the supplied document.
     *
     * @param document full document text
     * @return immutable list of diagnostics (empty when none)
     */
    @NotNull
    List<EditorDiagnostic> compute(String document);
}
