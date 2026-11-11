package dev.railroadide.railroad.plugin.spi.editor;

import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import java.util.Objects;

/** Replacement text for a half-open range in the expected snapshot's UTF-16 offsets. */
public record TextReplacement(TextRange range, String text) {
    /** Creates a replacement; document bounds and overlaps are checked at application. */
    public TextReplacement {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(text, "text");
    }
}
