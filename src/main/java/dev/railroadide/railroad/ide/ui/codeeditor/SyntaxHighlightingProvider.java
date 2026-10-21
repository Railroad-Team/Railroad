package dev.railroadide.railroad.ide.ui.codeeditor;

import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

/**
 * Computes CSS style spans for a complete source-text snapshot.
 */
@FunctionalInterface
public interface SyntaxHighlightingProvider {
    /**
     * Computes syntax styling for the supplied source snapshot.
     *
     * @param text complete source text to highlight
     * @return style spans whose collections identify CSS classes for each text range
     */
    StyleSpans<Collection<String>> compute(String text);
}
