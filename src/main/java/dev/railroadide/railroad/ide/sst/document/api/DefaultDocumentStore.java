package dev.railroadide.railroad.ide.sst.document.api;

import lombok.Synchronized;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultDocumentStore implements DocumentStore {
    private final Map<DocumentId, Entry> entries = new HashMap<>();

    @Synchronized
    @Override
    public void publishBacking(DocumentSnapshot snapshot) {
        Entry entry = entries.get(snapshot.id());
        if (entry == null) {
            entry = new Entry(snapshot, null);
        } else {
            if (entry.backing() != null && entry.backing().version().isAfter(snapshot.version()))
                throw new IllegalArgumentException(
                    "Cannot publish a backing snapshot with an older version than the current backing snapshot.");

            entry = new Entry(snapshot, entry.overlay());
        }

        entries.put(snapshot.id(), entry);
    }

    @Synchronized
    @Override
    public void publishOverlay(DocumentSnapshot snapshot) {
        Entry entry = entries.get(snapshot.id());
        if (entry == null) {
            entry = new Entry(null, new OverlayState(snapshot, null));
        } else {
            if (entry.overlay() != null && entry.overlay().snapshot().version().isAfter(snapshot.version()))
                throw new IllegalArgumentException(
                    "Cannot publish an overlay snapshot with an older version than the current overlay snapshot.");

            DocumentVersion baseBackingVersion = entry.overlay() != null
                ? entry.overlay().baseBackingVersion()
                : entry.backing().version();
            entry = new Entry(entry.backing(), new OverlayState(snapshot, baseBackingVersion));
        }

        entries.put(snapshot.id(), entry);
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
    }

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
