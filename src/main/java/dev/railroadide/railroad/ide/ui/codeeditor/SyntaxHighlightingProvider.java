package dev.railroadide.railroad.ide.ui.codeeditor;

import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

@FunctionalInterface
public interface SyntaxHighlightingProvider {
    StyleSpans<Collection<String>> compute(String text);
}
