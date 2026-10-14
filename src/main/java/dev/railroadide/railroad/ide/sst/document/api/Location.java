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
     * A text location with a zero-based line and UTF-16 column for the range start.
     */
    record TextLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        TextRange range,
        int line,
        int column) implements Location<TextRange> {

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
    }

    record BinaryLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        BinaryRange range) implements Location<BinaryRange> {
    }
}
