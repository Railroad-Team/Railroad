package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.ByteBuffer;

/**
 * An immutable, half-open range of byte offsets into a {@link BinaryDocumentSnapshot}.
 */
public final class BinaryRange extends Range<BinaryDocumentSnapshot> {
    /**
     * Creates a binary range with the given half-open bounds.
     * Snapshot bounds are checked when {@link #content(BinaryDocumentSnapshot)} is called.
     *
     * @param start inclusive start offset in bytes
     * @param end exclusive end offset in bytes
     * @throws IllegalArgumentException if {@code start} is negative or {@code end < start}
     */
    public BinaryRange(int start, int end) {
        super(start, end);
    }

    /**
     * Returns a read-only view over this range within the snapshot.
     * Each call returns a buffer positioned at zero with its limit and capacity
     * equal to {@link #length()}. An empty range returns an empty buffer, provided
     * its offset is within the snapshot bounds, including the end of the content.
     *
     * @param snapshot snapshot whose bytes are viewed
     * @return a read-only buffer containing the bytes between the start and end offsets
     * @throws NullPointerException if {@code snapshot} is {@code null}
     * @throws IndexOutOfBoundsException if this range extends beyond the snapshot's content
     */
    public ByteBuffer content(BinaryDocumentSnapshot snapshot) {
        return snapshot.bytes().slice(start, length());
    }
}
