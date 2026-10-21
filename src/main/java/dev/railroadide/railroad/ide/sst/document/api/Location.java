package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * Represents a location within a document.
 *
 * @param <R> The type of {@link Range} supported and thus, the type of supported {@link DocumentSnapshot}
 */
public sealed interface Location<R extends Range<?>> permits Location.TextLocation, Location.BinaryLocation {

    /**
     * The identity of the current document
     */
    DocumentId documentId();

    /**
     * The version of the current document
     */
    DocumentVersion documentVersion();

    /**
     * The targeted range of content within the document
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
     */
    record TextLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        TextRange range,
        int line,
        int column) implements Location<TextRange> {
        public TextLocation {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentVersion, "documentVersion");
            Objects.requireNonNull(range, "range");

            if (line < 0)
                throw new IllegalArgumentException("Line must be non-negative");

            if (column < 0)
                throw new IllegalArgumentException("Column must be non-negative");
        }

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

    record BinaryLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        BinaryRange range) implements Location<BinaryRange> {
        public BinaryLocation {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentVersion, "documentVersion");
            Objects.requireNonNull(range, "range");
        }

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
