package dev.railroadide.railroad.plugin.spi.editor;

/** A directed selection in UTF-16 offsets; equal anchor and caret denote a caret. */
public record TextSelection(int anchor, int caret) {
    /** Creates a selection; document bounds are validated when navigating. */
    public TextSelection {
        if (anchor < 0 || caret < 0)
            throw new IllegalArgumentException("Selection offsets must be non-negative");
    }
}
