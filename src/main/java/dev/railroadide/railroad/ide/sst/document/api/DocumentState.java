package dev.railroadide.railroad.ide.sst.document.api;

/**
 * Immutable view of a document's published snapshots and the backing revision on
 * which its overlay was created or last explicitly reconciled. Further overlay edits
 * retain that base revision.
 * Removing backing preserves the overlay and its base. This value retains its
 * snapshots and does not observe later changes to the store.
 *
 * @param backing backing snapshot, or {@code null} if absent or removed
 * @param overlay overlay snapshot, or {@code null} if none has been published
 * @param baseBackingVersion backing revision captured when the overlay was created
 *            or last explicitly reconciled; retained after backing removal and
 *            {@code null} if no overlay exists or it was created without backing
 */
public record DocumentState(DocumentSnapshot backing, DocumentSnapshot overlay, DocumentVersion baseBackingVersion) {
    /**
     * Returns whether an overlay's backing differs from its base or has disappeared.
     * An overlay created without backing becomes stale if backing is later published.
     * An overlay with an absent base is not stale while backing is also absent.
     * Without an overlay, this method always returns {@code false}.
     *
     * @return whether the overlay is based on outdated or unknown backing content
     */
    public boolean isStale() {
        if (overlay == null)
            return false;

        if (backing == null)
            return baseBackingVersion != null;

        return !backing.version().equals(baseBackingVersion);
    }
}
