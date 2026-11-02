package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable mapping of document revisions captured for a workspace query.
 * The mapping is copied, while immutable document snapshots retain their identity.
 * Revisions are scoped to the store that produced the capture.
 *
 * @param documents captured documents indexed by logical document identity
 * @param revision workspace revision captured atomically with the documents
 */
public record WorkspaceSnapshot(Map<DocumentId, DocumentSnapshot> documents, long revision) {
    /**
     * Copies the supplied mapping into an unmodifiable map.
     *
     * @param documents captured documents
     * @param revision captured workspace revision
     * @throws NullPointerException if the map or any key or value is {@code null}
     */
    public WorkspaceSnapshot {
        documents = Map.copyOf(documents);
    }

    /**
     * Returns the captured snapshot for a document.
     *
     * @param id logical document identity
     * @return captured snapshot, or empty if the document was not captured
     */
    public Optional<DocumentSnapshot> document(DocumentId id) {
        return Optional.ofNullable(documents.get(id));
    }
}
