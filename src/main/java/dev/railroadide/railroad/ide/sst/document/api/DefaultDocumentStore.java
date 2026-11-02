package dev.railroadide.railroad.ide.sst.document.api;

import lombok.Synchronized;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link DocumentStore} for one project. Snapshot and publication operations share
 * the instance lock supplied by Lombok's {@link Synchronized}, so validation,
 * replacement, revision updates, and capture are atomic with respect to one another.
 * <p>
 * Active document entries are independent of retained backing-version history.
 * Deleting backing may remove an active entry, but its highest backing version remains
 * recorded for the lifetime of this store. Captures contain active current snapshots
 * and the retained immutable configuration, without referencing this mutable store.
 */
public final class DefaultDocumentStore implements DocumentStore {
    private final String projectId;
    private final Map<DocumentId, Entry> entries = new HashMap<>();
    private final Map<DocumentId, DocumentVersion> lastBackingVersions = new HashMap<>();
    private final Map<DocumentId, DocumentVersion> latestVersions = new HashMap<>();
    private WorkspaceConfigurationSnapshot configuration;
    private long workspaceRevision;
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    @Override
    public ChangeSubscription onChange(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    private void workspaceChanged() {
        workspaceRevision++;
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                System.getLogger(DefaultDocumentStore.class.getName())
                    .log(System.Logger.Level.WARNING, "Workspace observer failed", exception);
            }
        }
    }

    @Synchronized
    @Override
    public void commitSaveAs(DocumentSnapshot savedSnapshot, DocumentState expectedState) {
        Objects.requireNonNull(savedSnapshot, "savedSnapshot");
        DocumentId id = savedSnapshot.id();
        if (!Objects.equals(state(id).orElse(null), expectedState))
            throw new IllegalStateException("Document changed while Save As was in progress.");

        DocumentSnapshot backing = expectedState == null ? null : expectedState.backing();
        DocumentSnapshot overlay = expectedState == null ? null : expectedState.overlay();
        DocumentVersion latest = latestVersions.get(id);
        boolean relocatingBacking = overlay == null && backing != null
            && backing.version().equals(savedSnapshot.version()) && hasSameContent(backing, savedSnapshot);
        if (!relocatingBacking && latest != null && (savedSnapshot.version().isBefore(latest)
            || expectedState == null && savedSnapshot.version().equals(latest)))
            throw new IllegalArgumentException("Save As cannot reuse an abandoned document revision.");
        if (overlay != null && !hasSameContent(overlay, savedSnapshot))
            throw new IllegalArgumentException("Saved content must match the editor overlay.");
        if (backing != null && savedSnapshot.version().equals(backing.version())
            && !hasSameContent(backing, savedSnapshot))
            throw new IllegalArgumentException("Content cannot change at the same backing version.");
        if (expectedState != null && expectedState.isStale()
            && (!savedSnapshot.version().isAfter(overlay.version())
                || backing != null && !savedSnapshot.version().isAfter(backing.version())))
            throw new IllegalArgumentException("Saving a stale overlay requires a newer version.");
        DocumentVersion lastBacking = lastBackingVersions.get(id);
        if (backing == null && lastBacking != null && !savedSnapshot.version().isAfter(lastBacking))
            throw new IllegalArgumentException("Reappearing backing requires a newer version.");

        entries.put(id, new Entry(savedSnapshot, null));
        lastBackingVersions.put(id, savedSnapshot.version());
        observeVersion(savedSnapshot);
        workspaceChanged();
    }

    /**
     * Creates a document store owned by one project, with workspace revision zero.
     * The initial configuration is retained as supplied and does not count as a
     * publication, regardless of its own configuration revision or module IDs.
     *
     * @param projectId nonblank identity retained by every captured workspace
     * @param initialConfiguration configuration retained by the initial workspace capture
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the project identity is blank
     */
    public DefaultDocumentStore(String projectId, WorkspaceConfigurationSnapshot initialConfiguration) {
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.configuration = Objects.requireNonNull(initialConfiguration, "initialConfiguration");
        if (this.projectId.isBlank())
            throw new IllegalArgumentException("Project ID cannot be blank");
    }

    @Synchronized
    @Override
    public Optional<DocumentVersion> lastBackingVersion(DocumentId id) {
        return Optional.ofNullable(lastBackingVersions.get(id));
    }

    @Synchronized
    @Override
    public Optional<DocumentVersion> latestVersion(DocumentId id) {
        return Optional.ofNullable(latestVersions.get(id));
    }

    private void observeVersion(DocumentSnapshot snapshot) {
        latestVersions.merge(snapshot.id(), snapshot.version(),
            (first, second) -> first.isAfter(second) ? first : second);
    }

    @Synchronized
    @Override
    public void removeOverlay(DocumentId id, DocumentVersion expectedOverlayVersion) {
        Entry entry = entries.get(id);
        if (entry == null || entry.overlay() == null
            || !entry.overlay().snapshot().version().equals(expectedOverlayVersion))
            throw new IllegalStateException("Cannot remove an absent or changed overlay.");

        if (entry.backing() == null) {
            entries.remove(id);
        } else {
            entries.put(id, new Entry(entry.backing(), null));
        }
        workspaceChanged();
    }

    @Synchronized
    @Override
    public void relocate(DocumentId id, DocumentUri uri) {
        Objects.requireNonNull(uri, "uri");
        Entry entry = entries.get(id);
        if (entry == null)
            return;

        DocumentSnapshot backing = relocated(entry.backing(), uri);
        OverlayState overlay = entry.overlay();
        DocumentSnapshot overlaySnapshot = overlay == null ? null : relocated(overlay.snapshot(), uri);
        if (backing == entry.backing() && (overlay == null || overlaySnapshot == overlay.snapshot()))
            return;

        entries.put(id, new Entry(backing,
            overlay == null ? null : new OverlayState(overlaySnapshot, overlay.baseBackingVersion())));
        workspaceChanged();
    }

    private static DocumentSnapshot relocated(DocumentSnapshot snapshot, DocumentUri uri) {
        if (snapshot == null || snapshot.uri().equals(uri))
            return snapshot;

        return switch (snapshot) {
            case TextDocumentSnapshot text -> new TextDocumentSnapshot(text.id(), uri, text.version(),
                text.languageId(), text.origin(), text.text(), text.encoding());
            case BinaryDocumentSnapshot binary -> new BinaryDocumentSnapshot(binary.id(), uri, binary.version(),
                binary.languageId(), binary.origin(), binary.bytes());
        };
    }

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

            OverlayState overlay = entry.overlay();
            if (overlay != null && overlay.snapshot().version().equals(snapshot.version())
                && !hasSameContent(overlay.snapshot(), snapshot))
                throw new IllegalArgumentException(
                    "Backing and overlay cannot have different content at the same document version.");

            entry = new Entry(snapshot, overlay);
        }

        entries.put(snapshot.id(), entry);
        lastBackingVersions.put(snapshot.id(), snapshot.version());
        observeVersion(snapshot);
        workspaceChanged();
    }

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

        workspaceChanged();
    }

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
            }

            if (backing != null && snapshot.version().equals(backing.version()) && !hasSameContent(backing, snapshot))
                throw new IllegalArgumentException(
                    "Backing and overlay cannot have different content at the same document version.");

            DocumentVersion baseBackingVersion;
            if (overlay != null) {
                baseBackingVersion = overlay.baseBackingVersion();
            } else {
                baseBackingVersion = backing != null ? backing.version() : null;
            }

            entry = new Entry(backing, new OverlayState(snapshot, baseBackingVersion));
        }

        entries.put(snapshot.id(), entry);
        observeVersion(snapshot);
        workspaceChanged();
    }

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
        observeVersion(resolvedSnapshot);
        workspaceChanged();
    }

    @Synchronized
    @Override
    public void commitOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion
    ) {
        DocumentSnapshot overlay = validateOverlayCommit(id, expectedBackingVersion, expectedOverlayVersion);
        replaceCommittedBacking(id, overlay);
    }

    @Synchronized
    @Override
    public void commitOverlay(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion,
        DocumentSnapshot savedSnapshot
    ) {
        Objects.requireNonNull(savedSnapshot, "savedSnapshot");
        DocumentSnapshot overlay = validateOverlayCommit(id, expectedBackingVersion, expectedOverlayVersion);
        if (!savedSnapshot.id().equals(id))
            throw new IllegalArgumentException(
                "Saved snapshot must have the same document ID as the committed overlay.");

        if (!savedSnapshot.version().equals(overlay.version()))
            throw new IllegalArgumentException("Saved snapshot must have the same version as the committed overlay.");

        if (!hasSameContent(overlay, savedSnapshot))
            throw new IllegalArgumentException("Saved snapshot must have the same content as the committed overlay.");

        replaceCommittedBacking(id, savedSnapshot);
    }

    private DocumentSnapshot validateOverlayCommit(
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

        return overlay.snapshot();
    }

    private void replaceCommittedBacking(DocumentId id, DocumentSnapshot savedSnapshot) {
        entries.put(id, new Entry(savedSnapshot, null));
        lastBackingVersions.put(id, savedSnapshot.version());
        workspaceChanged();
    }

    @Synchronized
    @Override
    public void acknowledgeSavedRevision(
        DocumentId id,
        DocumentVersion expectedBackingVersion,
        DocumentVersion expectedOverlayVersion,
        DocumentSnapshot savedSnapshot
    ) {
        Entry entry = entries.get(id);
        if (entry == null)
            throw new IllegalStateException(
                "Cannot acknowledge a saved revision for a document with no published snapshots.");

        DocumentSnapshot backing = entry.backing();
        if (backing == null)
            throw new IllegalStateException(
                "Cannot acknowledge a saved revision for a document with no published backing.");

        OverlayState overlay = entry.overlay();
        if (overlay == null)
            throw new IllegalStateException(
                "Cannot acknowledge a saved revision for a document with no published overlay.");

        Objects.requireNonNull(expectedBackingVersion, "expectedBackingVersion");
        Objects.requireNonNull(expectedOverlayVersion, "expectedOverlayVersion");
        Objects.requireNonNull(savedSnapshot, "savedSnapshot");

        if (!backing.version().equals(expectedBackingVersion))
            throw new IllegalStateException(
                "Cannot acknowledge a saved revision for a document whose backing version has changed since acknowledgement was requested.");

        if (!overlay.snapshot().version().equals(expectedOverlayVersion))
            throw new IllegalStateException(
                "Cannot acknowledge a saved revision for a document whose overlay version has changed since acknowledgement was requested.");

        if (!backing.version().equals(overlay.baseBackingVersion()))
            throw new IllegalStateException("Cannot acknowledge a saved revision for a stale overlay.");

        if (!savedSnapshot.id().equals(id))
            throw new IllegalArgumentException(
                "Saved snapshot must have the same document ID as the document being acknowledged.");

        if (!savedSnapshot.version().isAfter(backing.version())
            || !savedSnapshot.version().isBefore(overlay.snapshot().version()))
            throw new IllegalArgumentException(
                "Saved snapshot version must be strictly between the backing and overlay versions.");

        entries.put(id, new Entry(savedSnapshot, new OverlayState(overlay.snapshot(), savedSnapshot.version())));
        lastBackingVersions.put(id, savedSnapshot.version());
        workspaceChanged();
    }

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
        workspaceChanged();
    }

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
        observeVersion(restoredBacking);
        lastBackingVersions.put(id, restoredBacking.version());
        workspaceChanged();
    }

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

    @Synchronized
    @Override
    public void publishConfiguration(WorkspaceConfigurationSnapshot configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.revision() < this.configuration.revision())
            throw new IllegalArgumentException(
                "Cannot publish a configuration snapshot with an older revision than the current configuration revision.");

        if (configuration.revision() == this.configuration.revision()) {
            if (!configuration.moduleIds().equals(this.configuration.moduleIds())
                || !configuration.settings().equals(this.configuration.settings()))
                throw new IllegalArgumentException(
                    "Cannot publish a configuration snapshot with the same revision but different module IDs.");

            return;
        }

        this.configuration = configuration;
        workspaceChanged();
    }

    private static boolean hasSameContent(DocumentSnapshot backing, DocumentSnapshot restoredBacking) {
        return switch (backing) {
            case TextDocumentSnapshot text -> restoredBacking instanceof TextDocumentSnapshot restoredText
                && text.text().equals(restoredText.text());
            case BinaryDocumentSnapshot binary -> restoredBacking instanceof BinaryDocumentSnapshot restoredBinary
                && binary.bytes().equals(restoredBinary.bytes());
        };
    }

    private record Entry(
        DocumentSnapshot backing,
        OverlayState overlay
    ) {
    }

    private record OverlayState(
        DocumentSnapshot snapshot,
        DocumentVersion baseBackingVersion
    ) {
    }
}
