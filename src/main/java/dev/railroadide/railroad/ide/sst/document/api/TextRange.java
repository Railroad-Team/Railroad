package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.CharBuffer;

// SST-P0-031: Add validated half-open `TextRange`.
/**
 * A validated, half-open range of content within a {@link TextDocumentSnapshot}.
 */
public final class TextRange extends Range<TextDocumentSnapshot>
{
    public TextRange(int start, int end) {
        super(start, end);
    }

    /**
     *  Returns a read-only view over this range within the snapshot.
     */
    public CharBuffer content(TextDocumentSnapshot snapshot) {
        return CharBuffer.wrap(snapshot.text())
            .slice(start, length())
            .asReadOnlyBuffer();
    }
}
