package dev.railroadide.railroad.plugin.spi.editor;

/**
 * A directed selection in UTF-16 offsets; equal anchor and caret denote a caret.
 *
 * @param anchor non-negative fixed end of the selection
 * @param caret non-negative active end of the selection, which may precede the anchor
 */
public record TextSelection(int anchor, int caret) {
    /**
     * Creates a selection; document bounds are validated when navigating.
     *
     * @param anchor non-negative fixed end of the selection
     * @param caret non-negative active end of the selection
     * @throws IllegalArgumentException if either offset is negative
     */
    public TextSelection {
        if (anchor < 0 || caret < 0)
            throw new IllegalArgumentException("Selection offsets must be non-negative");
    }
}
