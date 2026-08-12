package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.ByteBuffer;

// SST-P0-032: Add validated half-open `ByteRange`.
/**
 * A validated, half-open range of content within a {@link BinaryDocumentSnapshot}.
 */
public final class BinaryRange extends Range<BinaryDocumentSnapshot>
{
    public BinaryRange(int start, int end) {
        super(start, end);
    }

    /**
     * Returns a read-only view of the complete content.
     */
    public ByteBuffer content(BinaryDocumentSnapshot snapshot) {
        return snapshot.bytes().slice(start, length());
    }
}
