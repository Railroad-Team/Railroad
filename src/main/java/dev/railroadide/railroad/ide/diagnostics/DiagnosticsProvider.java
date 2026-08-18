package dev.railroadide.railroad.ide.diagnostics;

import org.jetbrains.annotations.NotNull;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;

import java.util.List;

/**
 * Provides language diagnostics for a document snapshot.
 */
public interface DiagnosticsProvider<D extends EditorDiagnostic> {
    /**
     * Computes diagnostics for the supplied {@link DocumentSnapshot}.
     *
     * @param snapshot full document text
     * @return immutable list of diagnostics (empty when none)
     */
    @NotNull
    List<D> compute(DocumentSnapshot snapshot);
}
