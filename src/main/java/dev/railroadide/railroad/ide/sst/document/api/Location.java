package dev.railroadide.railroad.ide.sst.document.api;

// SST-P0-034: Add a language-neutral `Location` containing:
//  document identity, snapshot version, and range.
/**
 * Represents a location within a document.
 *
 * @param <S> The type of {@link Range} supported and thus, the type of supported {@link DocumentSnapshot}
 */
public sealed interface Location<R extends Range<?>>
    permits Location.TextLocation, Location.BinaryLocation {

    /** The identity of the current document */
    public DocumentId documentId();

    /** The version of the current document */
    public DocumentVersion documentVersion();

    /** The targeted range of content within the document */
    public R range();

    public final record TextLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        TextRange range,
        int line,
        int column) implements Location<TextRange> {

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }

        public static TextLocation from(TextDocumentSnapshot snapshot, int start, int end) {
            return new TextLocation(
                snapshot.id(),
                snapshot.version(),
                new TextRange(start, end),
                computeLine(snapshot.text().toCharArray(), start),
                computeColumn(snapshot.text().toCharArray(), start));
        }

        private static int computeLine(char[] source, int position) {
            int line = 1;
            int bound = Math.clamp(source.length, 0, position);
            for (int index = 0; index < bound; index++) {
                if (source[index] == '\n') {
                    line++;
                }
            }
            return line;
        }

        private static int computeColumn(char[] source, int position) {
            int column = 1;
            int index = Math.clamp(source.length, 0, position) - 1;
            for (; index >= 0; index--) {
                char ch = source[index];
                if (ch == '\n' || ch == '\r')
                    break;
                column++;
            }
            return column;
        }
    }

    public final record BinaryLocation(
        DocumentId documentId,
        DocumentVersion documentVersion,
        BinaryRange range) implements Location<BinaryRange> {
    }
}
