package dev.railroadide.railroad.ide.sst.document.api;

import lombok.Synchronized;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory {@link DocumentStore} for one project. All public store operations share
 * the instance lock supplied by Lombok's {@link Synchronized}, so validation,
 * replacement, revision updates, and capture are atomic with respect to one another.
 * <p>
 * Active document entries are independent of retained backing-version history.
 * Deleting backing may remove an active entry, but its highest backing version remains
 * recorded for the lifetime of this store. Captures contain active current snapshots
 * and the retained immutable configuration, without referencing this mutable store.
 */
public final class DefaultDocumentStore implements DocumentStore {
    /** Project identity shared by every workspace capture. */
    private final String projectId;
    /** Active backing and overlay snapshots, guarded by the instance lock. */
    private final Map<DocumentId, Entry> entries = new HashMap<>();
    /** Highest backing versions, including commit/discard revisions, retained after deletion. */
    private final Map<DocumentId, DocumentVersion> lastBackingVersions = new HashMap<>();
    /** Published immutable configuration, guarded by the instance lock. */
    private WorkspaceConfigurationSnapshot configuration;
    /** Store-state revision, independent of document and configuration revisions. */
    private long workspaceRevision;

    /**
     * Creates a document store owned by one project, with workspace revision zero.
     * The initial configuration is retained as supplied and does not count as a
     * publication, regardless of its own configuration revision or module IDs.
     *
     * @param projectId identity retained by every captured workspace
     * @param initialConfiguration configuration retained by the initial workspace capture
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultDocumentStore(String projectId, WorkspaceConfigurationSnapshot initialConfiguration) {
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.configuration = Objects.requireNonNull(initialConfiguration, "initialConfiguration");
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void publishBacking(DocumentSnapshot snapshot) {
        Entry entry = entries.get(snapshot.id());
        if (entry == null || entry.backing() == null) {
            DocumentVersion lastBackingVersion = lastBackingVersions.get(snapshot.id());
            if (lastBackingVersion != null && !snapshot.version().isAfter(lastBackingVersion))
                throw new IllegalArgumentException(
                    "Reappearing backing must have a newer version than its last published backing revision.");
        }

        if (entry == null) {
            entry = new Entry(snapshot, null);
        } else {
            DocumentSnapshot backing = entry.backing();
            if (backing == snapshot)
                return;

            if (backing != null) {
                if (backing.version().isAfter(snapshot.version()))
                    throw new IllegalArgumentException(
                        "Cannot publish a backing snapshot with an older version than the current backing snapshot.");

                if (backing.version().equals(snapshot.version()) && !hasSameContent(backing, snapshot))
                    throw new IllegalArgumentException(
                        "A backing snapshot with the same version as the current backing must have the same content.");
            }

            entry = new Entry(snapshot, entry.overlay());
        }

        entries.put(snapshot.id(), entry);
        lastBackingVersions.put(snapshot.id(), snapshot.version());
        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void removeBacking(DocumentId id, DocumentVersion expectedBackingVersion) {
        Entry entry = entries.get(id);
        if (entry == null)
            throw new IllegalStateException("Cannot remove backing for a document with no published snapshots.");

        DocumentSnapshot backing = entry.backing();
        if (backing == null)
            throw new IllegalStateException("Cannot remove backing for a document with no published backing.");

        Objects.requireNonNull(expectedBackingVersion, "expectedBackingVersion");
        if (!backing.version().equals(expectedBackingVersion))
            throw new IllegalStateException(
                "Cannot remove backing for a document whose backing version has changed since removal was requested.");

        if (entry.overlay() == null) {
            entries.remove(id);
        } else {
            entries.put(id, new Entry(null, entry.overlay()));
        }

        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void publishOverlay(DocumentSnapshot snapshot) {
        Entry entry = entries.get(snapshot.id());
        if (entry == null) {
            entry = new Entry(null, new OverlayState(snapshot, null));
        } else {
            DocumentSnapshot backing = entry.backing();
            OverlayState overlay = entry.overlay();
            if (overlay != null && overlay.snapshot() == snapshot)
                return;

            if (overlay != null && overlay.snapshot().version().isAfter(snapshot.version()))
                throw new IllegalArgumentException(
                    "Cannot publish an overlay snapshot with an older version than the current overlay snapshot.");

            if (overlay != null && overlay.snapshot().version().equals(snapshot.version())
                && !hasSameContent(overlay.snapshot(), snapshot))
                throw new IllegalArgumentException(
                    "An overlay snapshot with the same version as the current overlay must have the same content.");

            if (overlay == null && backing != null) {
                if (snapshot.version().isBefore(backing.version()))
                    throw new IllegalArgumentException(
                        "Cannot publish an initial overlay with an older version than the backing snapshot.");

                if (snapshot.version().equals(backing.version()) && !hasSameContent(backing, snapshot))
                    throw new IllegalArgumentException(
                        "An initial overlay with the same version as backing must have the same content.");
            }

            DocumentVersion baseBackingVersion;
            if (overlay != null) {
                baseBackingVersion = overlay.baseBackingVersion();
            } else {
                baseBackingVersion = backing != null ? backing.version() : null;
            }

            entry = new Entry(backing, new OverlayState(snapshot, baseBackingVersion));
        }

        entries.put(snapshot.id(), entry);
        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public Optional<DocumentSnapshot> current(DocumentId id) {
        Entry entry = entries.get(id);
        if (entry == null)
            return Optional.empty();

        OverlayState overlay = entry.overlay();
        if (overlay != null)
            return Optional.of(overlay.snapshot());

        DocumentSnapshot backing = entry.backing();
        if (backing != null)
            return Optional.of(backing);

        return Optional.empty();
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public Optional<DocumentState> state(DocumentId id) {
        Entry entry = entries.get(id);
        if (entry == null)
            return Optional.empty();

        OverlayState overlay = entry.overlay();
        return Optional.of(new DocumentState(
            entry.backing(),
            overlay != null ? overlay.snapshot() : null,
            overlay != null ? overlay.baseBackingVersion() : null));
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void reconcileOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion,
        DocumentSnapshot resolvedSnapshot
    ) {
        Entry entry = entries.get(id);
        if (entry == null)
            throw new IllegalStateException("Cannot reconcile overlay for a document with no published snapshots.");

        OverlayState overlay = entry.overlay();
        if (overlay == null)
            throw new IllegalStateException("Cannot reconcile overlay for a document with no published overlay.");

        DocumentSnapshot backing = entry.backing();
        if (backing == null)
            throw new IllegalStateException("Cannot reconcile overlay for a document with no published backing.");

        Objects.requireNonNull(expectedBackingVersion, "expectedBackingVersion");
        Objects.requireNonNull(expectedOverlayVersion, "expectedOverlayVersion");
        Objects.requireNonNull(resolvedSnapshot, "resolvedSnapshot");

        if (!backing.version().equals(expectedBackingVersion))
            throw new IllegalStateException(
                "Cannot reconcile overlay for a document whose backing version has changed since reconciliation was requested.");

        if (!overlay.snapshot().version().equals(expectedOverlayVersion))
            throw new IllegalStateException(
                "Cannot reconcile overlay for a document whose overlay version has changed since reconciliation was requested.");

        if (!resolvedSnapshot.id().equals(id))
            throw new IllegalArgumentException(
                "Resolved snapshot must have the same document ID as the overlay being reconciled.");

        if (!resolvedSnapshot.version().isAfter(expectedOverlayVersion))
            throw new IllegalArgumentException(
                "Resolved snapshot must have a version greater than the overlay being reconciled.");

        if (!resolvedSnapshot.version().isAfter(expectedBackingVersion))
            throw new IllegalArgumentException(
                "Resolved snapshot must have a version greater than the backing version.");

        entries.put(id, new Entry(backing, new OverlayState(resolvedSnapshot, expectedBackingVersion)));
        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void commitOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion
    ) {
        Entry entry = entries.get(id);
        if (entry == null)
            throw new IllegalStateException("Cannot commit overlay for a document with no published snapshots.");

        DocumentSnapshot backing = entry.backing();
        if (backing == null)
            throw new IllegalStateException("Cannot commit overlay for a document with no published backing.");

        OverlayState overlay = entry.overlay();
        if (overlay == null)
            throw new IllegalStateException("Cannot commit overlay for a document with no published overlay.");

        Objects.requireNonNull(expectedBackingVersion, "expectedBackingVersion");
        Objects.requireNonNull(expectedOverlayVersion, "expectedOverlayVersion");

        if (!backing.version().equals(expectedBackingVersion))
            throw new IllegalStateException(
                "Cannot commit overlay for a document whose backing version has changed since commit was requested.");

        if (!overlay.snapshot().version().equals(expectedOverlayVersion))
            throw new IllegalStateException(
                "Cannot commit overlay for a document whose overlay version has changed since commit was requested.");

        if (!backing.version().equals(overlay.baseBackingVersion()))
            throw new IllegalStateException("Cannot commit a stale overlay.");

        if (!overlay.snapshot().version().isAfter(backing.version()))
            throw new IllegalStateException("Committed overlay must have a newer version than its backing.");

        entries.put(id, new Entry(overlay.snapshot(), null));
        lastBackingVersions.put(id, overlay.snapshot().version());
        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void removeUnchangedOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion
    ) {
        Entry entry = entries.get(id);
        if (entry == null)
            throw new IllegalStateException(
                "Cannot remove unchanged overlay for a document with no published snapshots.");

        DocumentSnapshot backing = entry.backing();
        if (backing == null)
            throw new IllegalStateException(
                "Cannot remove unchanged overlay for a document with no published backing.");

        OverlayState overlay = entry.overlay();
        if (overlay == null)
            throw new IllegalStateException(
                "Cannot remove unchanged overlay for a document with no published overlay.");

        Objects.requireNonNull(expectedBackingVersion, "expectedBackingVersion");
        Objects.requireNonNull(expectedOverlayVersion, "expectedOverlayVersion");

        if (!backing.version().equals(expectedBackingVersion))
            throw new IllegalStateException(
                "Cannot remove unchanged overlay for a document whose backing version has changed since removal was requested.");

        if (!overlay.snapshot().version().equals(expectedOverlayVersion))
            throw new IllegalStateException(
                "Cannot remove unchanged overlay for a document whose overlay version has changed since removal was requested.");

        if (!backing.version().equals(overlay.baseBackingVersion()))
            throw new IllegalStateException("Cannot remove a stale overlay as unchanged.");

        if (!backing.version().equals(overlay.snapshot().version()) || !hasSameContent(backing, overlay.snapshot()))
            throw new IllegalStateException(
                "Only an overlay with the same version and content as backing can be removed without a new revision.");

        entries.put(id, new Entry(backing, null));
        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void discardOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion,
        DocumentSnapshot restoredBacking
    ) {
        Entry entry = entries.get(id);
        if (entry == null)
            throw new IllegalStateException("Cannot discard overlay for a document with no published snapshots.");

        DocumentSnapshot backing = entry.backing();
        if (backing == null)
            throw new IllegalStateException("Cannot discard overlay for a document with no published backing.");

        OverlayState overlay = entry.overlay();
        if (overlay == null)
            throw new IllegalStateException("Cannot discard overlay for a document with no published overlay.");

        Objects.requireNonNull(expectedBackingVersion, "expectedBackingVersion");
        Objects.requireNonNull(expectedOverlayVersion, "expectedOverlayVersion");
        Objects.requireNonNull(restoredBacking, "restoredBacking");

        if (!backing.version().equals(expectedBackingVersion))
            throw new IllegalStateException(
                "Cannot discard overlay for a document whose backing version has changed since discard was requested.");

        if (!overlay.snapshot().version().equals(expectedOverlayVersion))
            throw new IllegalStateException(
                "Cannot discard overlay for a document whose overlay version has changed since discard was requested.");

        if (!restoredBacking.id().equals(id))
            throw new IllegalArgumentException(
                "Restored backing must have the same document ID as the overlay being discarded.");

        if (!hasSameContent(backing, restoredBacking))
            throw new IllegalArgumentException("Restored backing must have the same content as the current backing.");

        if (!backing.uri().equals(restoredBacking.uri())
            || !backing.languageId().equals(restoredBacking.languageId())
            || !backing.origin().equals(restoredBacking.origin()))
            throw new IllegalArgumentException(
                "Restored backing must preserve the current backing's URI, language, and origin.");

        if (backing instanceof TextDocumentSnapshot text
            && restoredBacking instanceof TextDocumentSnapshot restoredText
            && !text.encoding().equals(restoredText.encoding()))
            throw new IllegalArgumentException("Restored backing must preserve the current backing's encoding.");

        if (!restoredBacking.version().isAfter(backing.version())
            || !restoredBacking.version().isAfter(overlay.snapshot().version()))
            throw new IllegalArgumentException(
                "Restored backing must have a version greater than both the backing and overlay versions.");

        entries.put(id, new Entry(restoredBacking, null));
        lastBackingVersions.put(id, restoredBacking.version());
        workspaceRevision++;
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public WorkspaceSnapshot capture() {
        Map<DocumentId, DocumentSnapshot> capturedDocuments = new HashMap<>();
        for (Map.Entry<DocumentId, Entry> entry : this.entries.entrySet()) {
            DocumentId id = entry.getKey();
            Entry value = entry.getValue();
            if (value.backing() == null && value.overlay() == null)
                throw new IllegalStateException("Cannot capture a document with no published snapshots.");

            DocumentSnapshot snapshot = value.overlay() != null
                ? value.overlay().snapshot()
                : value.backing();
            capturedDocuments.put(id, snapshot);
        }

        return new WorkspaceSnapshot(projectId, capturedDocuments, configuration, workspaceRevision);
    }

    /** {@inheritDoc} */
    @Synchronized
    @Override
    public void publishConfiguration(WorkspaceConfigurationSnapshot configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.revision() < this.configuration.revision())
            throw new IllegalArgumentException(
                "Cannot publish a configuration snapshot with an older revision than the current configuration revision.");

        if (configuration.revision() == this.configuration.revision()) {
            if (!configuration.moduleIds().equals(this.configuration.moduleIds()))
                throw new IllegalArgumentException(
                    "Cannot publish a configuration snapshot with the same revision but different module IDs.");

            return;
        }

        this.configuration = configuration;
        workspaceRevision++;
    }

    /**
     * Compares text characters or complete binary bytes without comparing metadata.
     * Text and binary snapshots never have the same content representation.
     *
     * @param backing existing snapshot to compare
     * @param restoredBacking candidate snapshot to compare
     * @return whether both snapshots have the same representation and content
     */
    private static boolean hasSameContent(DocumentSnapshot backing, DocumentSnapshot restoredBacking) {
        return switch (backing) {
            case TextDocumentSnapshot text -> restoredBacking instanceof TextDocumentSnapshot restoredText
                && text.text().equals(restoredText.text());
            case BinaryDocumentSnapshot binary -> restoredBacking instanceof BinaryDocumentSnapshot restoredBinary
                && binary.bytes().equals(restoredBinary.bytes());
        };
    }

    /**
     * Published state of an active document; at least one snapshot is present.
     *
     * @param backing current backing snapshot, or {@code null} if absent
     * @param overlay current overlay state, or {@code null} if absent
     */
    private record Entry(
        DocumentSnapshot backing,
        OverlayState overlay
    ) {
    }

    /**
     * Immutable overlay snapshot and the backing revision on which it is based.
     *
     * @param snapshot published editor snapshot
     * @param baseBackingVersion backing revision at creation or reconciliation, or
     *            {@code null} if the overlay was created without backing
     */
    private record OverlayState(
        DocumentSnapshot snapshot,
        DocumentVersion baseBackingVersion
    ) {
    }
}
