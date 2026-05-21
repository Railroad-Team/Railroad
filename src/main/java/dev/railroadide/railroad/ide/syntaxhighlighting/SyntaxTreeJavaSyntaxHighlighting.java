package dev.railroadide.railroad.ide.syntaxhighlighting;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SyntaxTreeJavaSyntaxHighlighting {
    private static final Set<String> OPERATOR_TOKENS = Set.of(
            "=", ">", "<", "!", "?", ":", "->", "::",
            "==", ">=", "<=", "!=", "&&", "||",
            "+", "-", "++", "--", "*", "/", "%", "~",
            "&", "|", "^", "<<", ">>", ">>>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "<<=", ">>=", ">>>="
    );

    private SyntaxTreeJavaSyntaxHighlighting() {
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        var tree = JavaSyntaxParser.parse(text);
        var spansBuilder = new StyleSpansBuilder<Collection<String>>();
        appendNode(tree.root(), spansBuilder);
        return spansBuilder.create();
    }

    private static void appendNode(SyntaxNode node, StyleSpansBuilder<Collection<String>> spansBuilder) {
        if (node instanceof SyntaxToken token) {
            String tokenText = token.text();
            if (tokenText.isEmpty())
                return;

            String style = styleClassForToken(token);
            if (style.isEmpty()) {
                spansBuilder.add(Collections.emptyList(), tokenText.length());
            } else {
                spansBuilder.add(List.of(style), tokenText.length());
            }

            return;
        }

        for (SyntaxNode child : node.children()) {
            appendNode(child, spansBuilder);
        }
    }

    private static String styleClassForToken(SyntaxToken token) {
        String kind = token.kind().id().toUpperCase(Locale.ROOT);
        if (kind.contains("LINE_COMMENT") || kind.contains("BLOCK_COMMENT") || kind.contains("JAVADOC_COMMENT"))
            return "comment";
        if (kind.endsWith("_KEYWORD"))
            return "keyword";
        if (kind.contains("STRING_LITERAL") || kind.contains("CHARACTER_LITERAL") || kind.contains("TEXT_BLOCK_LITERAL"))
            return "literal";
        if (kind.contains("NUMBER_") || kind.contains("BOOLEAN_LITERAL") || kind.contains("NULL_LITERAL"))
            return "literal";

        String text = token.text();
        if (text.equals("@"))
            return "annotation";
        if (OPERATOR_TOKENS.contains(text))
            return "operator";

        return "";
    }
}
