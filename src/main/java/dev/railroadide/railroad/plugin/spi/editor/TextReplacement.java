package dev.railroadide.railroad.plugin.spi.editor;

import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import java.util.Objects;

/**
 * Replacement text for a half-open range in the expected snapshot's UTF-16 offsets.
 *
 * @param range range to replace; an empty range inserts text at its start
 * @param text replacement text, or an empty string to delete the range
 */
public record TextReplacement(TextRange range, String text) {
    /**
     * Creates a replacement; document bounds and overlaps are checked at application.
     *
     * @param range range in the expected snapshot's UTF-16 offsets
     * @param text replacement text
     * @throws NullPointerException if the range or text is {@code null}
     */
    public TextReplacement {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(text, "text");
    }
}
