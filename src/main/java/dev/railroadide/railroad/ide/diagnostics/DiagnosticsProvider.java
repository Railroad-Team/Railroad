package dev.railroadide.railroad.ide.diagnostics;

import org.jetbrains.annotations.NotNull;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;

import java.util.List;

/**
 * Provides language diagnostics for a document snapshot.
 *
 * @param <D> the diagnostic type produced by this provider
 */
public interface DiagnosticsProvider<D extends EditorDiagnostic<?>> {
    /**
     * Computes diagnostics for the supplied {@link DocumentSnapshot}.
     *
     * @param snapshot full document text
     * @return immutable list of diagnostics (empty when none)
     */
    @NotNull
    List<D> compute(DocumentSnapshot snapshot);

    /**
     * Computes diagnostics within one captured workspace, delegating to existing providers by default.
     *
     * @param workspace immutable request workspace
     * @param id document to inspect
     * @return diagnostics for the captured revision
     */
    default @NotNull List<D> compute(WorkspaceSnapshot workspace, DocumentId id) {
        return compute(workspace.document(id).orElseThrow());
    }
}
