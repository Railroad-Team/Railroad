package dev.railroadide.railroad.ide.sst.document.api;

/**
 * Immutable view of a document's published snapshots and the backing revision on
 * which its overlay was created or last explicitly reconciled. Further overlay edits
 * retain that base revision.
 *
 * @param backing backing snapshot, or {@code null} if none has been published
 * @param overlay overlay snapshot, or {@code null} if none has been published
 * @param baseBackingVersion backing revision captured when the overlay was created
 *            or last explicitly reconciled, or {@code null} if there was no backing or overlay
 */
public record DocumentState(DocumentSnapshot backing, DocumentSnapshot overlay, DocumentVersion baseBackingVersion) {
    /**
     * Returns whether an overlay exists alongside backing that differs from its base.
     * An overlay created without backing becomes stale if backing is later published.
     *
     * @return whether the overlay is based on outdated or unknown backing content
     */
    public boolean isStale() {
        return backing != null
            && overlay != null
            && !backing.version().equals(baseBackingVersion);
    }
}
