package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable mapping of document revisions captured for a workspace query.
 * The mapping is copied, while immutable document snapshots retain their identity.
 * Revisions are scoped to the store that produced the capture.
 * The immutable configuration and document snapshots retain their original instances;
 * later store changes cannot change this value. Workspace and configuration revisions
 * are independent, and equal workspace revisions from different stores do not imply
 * related state.
 * <p>
 * {@link DocumentStore#capture()} constructs a consistent view of published store
 * state under the store's lock. This value has no connection to the live store and
 * does not represent a simultaneous snapshot of filesystem contents.
 *
 * @param projectId identity of the project owning the captured workspace
 * @param documents captured documents indexed by logical document identity
 * @param configuration captured workspace configuration
 * @param revision workspace revision captured atomically with documents and configuration
 */
public record WorkspaceSnapshot(
    String projectId,
    Map<DocumentId, DocumentSnapshot> documents,
    WorkspaceConfigurationSnapshot configuration,
    long revision
) {
    /**
     * Copies the supplied mapping into an unmodifiable map.
     * Document snapshots and configuration are already immutable and are retained directly.
     *
     * @param projectId identity of the project owning the captured workspace
     * @param documents captured documents
     * @param configuration captured workspace configuration
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
     * @throws NullPointerException if the document identity is {@code null}
     */
    public Optional<DocumentSnapshot> document(DocumentId id) {
        return Optional.ofNullable(documents.get(id));
    }
}
