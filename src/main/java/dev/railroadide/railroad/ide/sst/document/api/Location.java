package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * Represents a location within a document.
 *
 * @param <R> The type of {@link Range} supported and thus, the type of supported {@link DocumentSnapshot}
 */
public sealed interface Location<R extends Range<?>> permits Location.TextLocation, Location.BinaryLocation {

    /**
     * Returns the identity of the targeted document.
     *
     * @return the logical document identity
     */
    DocumentId documentId();

    /**
     * Returns the content revision targeted by this location.
     *
     * @return the document version
     */
    DocumentVersion documentVersion();

    /**
     * Returns the targeted range of content within the document.
     *
     * @return the half-open content range
     */
    R range();

    /**
     * Validates that the location is still valid against the given snapshot.
     *
     * @param snapshot The snapshot to validate against
     * @throws IllegalArgumentException if the location is no longer valid against the given snapshot
     */
    void validateAgainst(DocumentSnapshot snapshot);

    /**
     * A text location with a zero-based line and UTF-16 column for the range start.
     *
     * @param documentId the targeted document identity
     * @param documentVersion the targeted content revision
     * @param range the half-open UTF-16 offset range
     * @param line the zero-based line of the range start
     * @param column the zero-based UTF-16 column of the range start
     */
    record TextLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        TextRange range,
        int line,
        int column
    ) implements Location<TextRange> {
        /**
         * Creates a text location with non-negative line and column coordinates.
         * Use {@link #from(TextDocumentSnapshot, int, int)} to derive coordinates from a snapshot.
         *
         * @param documentId the targeted document identity
         * @param documentVersion the targeted content revision
         * @param range the half-open UTF-16 offset range
         * @param line the zero-based line of the range start
         * @param column the zero-based UTF-16 column of the range start
         * @throws NullPointerException if the identity, version, or range is null
         * @throws IllegalArgumentException if the line or column is negative
         */
        public TextLocation {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentVersion, "documentVersion");
            Objects.requireNonNull(range, "range");

            if (line < 0)
                throw new IllegalArgumentException("Line must be non-negative");

            if (column < 0)
                throw new IllegalArgumentException("Column must be non-negative");
        }

        /**
         * Creates a location whose bounds and start coordinates are derived from a text snapshot.
         *
         * @param snapshot the targeted text revision
         * @param start the inclusive UTF-16 start offset
         * @param end the exclusive UTF-16 end offset
         * @return a validated location in the snapshot
         * @throws NullPointerException if the snapshot is null
         * @throws IllegalArgumentException if the bounds are negative or reversed
         * @throws IndexOutOfBoundsException if either bound exceeds the text length
         */
        public static TextLocation from(TextDocumentSnapshot snapshot, int start, int end) {
            Objects.requireNonNull(snapshot, "snapshot");

            var range = new TextRange(start, end);
            LineMap lineMap = snapshot.lineMap();

            TextPosition startPosition = lineMap.positionAt(start);
            lineMap.positionAt(end); // This just validates the end position
            return new TextLocation(
                snapshot.id(),
                snapshot.version(),
                range,
                startPosition.line(),
                startPosition.column());
        }

        @Override
        public void validateAgainst(DocumentSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!(snapshot instanceof TextDocumentSnapshot textSnapshot))
                throw new IllegalArgumentException("Snapshot is not a text document snapshot");

            if (!snapshot.id().equals(documentId()))
                throw new IllegalArgumentException("Snapshot document ID does not match location document ID");

            if (!snapshot.version().equals(documentVersion()))
                throw new IllegalArgumentException(
                    "Snapshot document version does not match location document version");

            LineMap lineMap = textSnapshot.lineMap();
            TextPosition expected = lineMap.positionAt(range.start);
            lineMap.positionAt(range.end);

            if (line != expected.line() || column != expected.column())
                throw new IllegalArgumentException("Location line and column do not match range start");
        }
    }

    /**
     * A byte range in a specific binary document revision.
     *
     * @param documentId the targeted document identity
     * @param documentVersion the targeted content revision
     * @param range the half-open byte offset range
     */
    record BinaryLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        BinaryRange range
    ) implements Location<BinaryRange> {
        /**
         * Creates a binary location; snapshot bounds are checked by {@link #validateAgainst(DocumentSnapshot)}.
         *
         * @param documentId the targeted document identity
         * @param documentVersion the targeted content revision
         * @param range the half-open byte offset range
         * @throws NullPointerException if any argument is null
         */
        public BinaryLocation {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentVersion, "documentVersion");
            Objects.requireNonNull(range, "range");
        }

        /**
         * Creates a location whose byte range is validated against a binary snapshot.
         *
         * @param snapshot the targeted binary revision
         * @param start the inclusive byte start offset
         * @param end the exclusive byte end offset
         * @return a validated location in the snapshot
         * @throws NullPointerException if the snapshot is null
         * @throws IllegalArgumentException if the bounds are negative or reversed
         * @throws IndexOutOfBoundsException if the range exceeds the snapshot size
         */
        public static BinaryLocation from(BinaryDocumentSnapshot snapshot, int start, int end) {
            Objects.requireNonNull(snapshot, "snapshot");

            var range = new BinaryRange(start, end);
            var location = new BinaryLocation(
                snapshot.id(),
                snapshot.version(),
                range);
            location.validateAgainst(snapshot);
            return location;
        }

        @Override
        public void validateAgainst(DocumentSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!(snapshot instanceof BinaryDocumentSnapshot binarySnapshot))
                throw new IllegalArgumentException("Snapshot is not a binary document snapshot");

            if (!snapshot.id().equals(documentId()))
                throw new IllegalArgumentException("Snapshot document ID does not match location document ID");

            if (!snapshot.version().equals(documentVersion()))
                throw new IllegalArgumentException(
                    "Snapshot document version does not match location document version");

            if (range.end > binarySnapshot.size())
                throw new IndexOutOfBoundsException("Binary range exceeds snapshot size");
        }
    }
}
