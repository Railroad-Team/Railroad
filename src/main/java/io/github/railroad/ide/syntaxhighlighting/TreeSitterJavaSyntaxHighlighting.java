package io.github.railroad.ide.syntaxhighlighting;

import io.github.railroad.Railroad;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTreeCursor;
import org.treesitter.TreeSitterJava;

import java.util.Collection;
import java.util.Collections;

public class TreeSitterJavaSyntaxHighlighting {
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        long start = System.currentTimeMillis();

        var highlighter = new SyntaxHighlighter(text);
        highlighter.traverseTree(null, highlighter.rootNode);

        var styles = highlighter.spansBuilder.create();
        Railroad.LOGGER.info("Computed highlighting in {} ms", System.currentTimeMillis() - start);
        return styles;
    }

    private static class SyntaxHighlighter {
        private final StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        private final String text;
        private final TSNode rootNode;
        private int currentPosition;

        public SyntaxHighlighter(String text) {
            this.text = text;

            var parser = new TSParser();
            parser.setLanguage(new TreeSitterJava());

            var tree = parser.parseString(null, text);
            this.rootNode = tree.getRootNode();
        }

        public void traverseTree(TSTreeCursor cursor, TSNode node) {
            cursor = cursor == null ? new TSTreeCursor(node) : cursor;

            do {
                TSNode currentNode = cursor.currentNode();
                String type = currentNode.getType();
                int start = currentNode.getStartByte();
                int end = currentNode.getEndByte();

                if (this.currentPosition < start) {
                    spansBuilder.add(Collections.emptyList(), start - this.currentPosition);
                }

                if(!cursor.gotoFirstChild()) {
                    System.out.println(type + ": " + text.substring(start, end));

                    switch (type) {
                        case "line_comment" -> spansBuilder.add(Collections.singleton("comment"), end - start);
                        case "string_literal" -> spansBuilder.add(Collections.singleton("string"), end - start);
                        case "decimal_integer_literal" ->
                                spansBuilder.add(Collections.singleton("number"), end - start);
                        case "identifier" -> spansBuilder.add(Collections.singleton("name"), end - start);
                        case "package" -> spansBuilder.add(Collections.singleton("package"), end - start);
                        case "modifiers" -> spansBuilder.add(Collections.singleton("modifier"), end - start);
                        case "import" -> spansBuilder.add(Collections.singleton("import"), end - start);
                        default -> spansBuilder.add(Collections.emptyList(), end - start);
                    }
                } else {
                    cursor.gotoParent();
                }

                this.currentPosition = end;

                // Recursively traverse children
                if (cursor.gotoFirstChild()) {
                    traverseTree(cursor, currentNode);
                    cursor.gotoParent();
                }
            } while (cursor.gotoNextSibling());
        }
    }
}