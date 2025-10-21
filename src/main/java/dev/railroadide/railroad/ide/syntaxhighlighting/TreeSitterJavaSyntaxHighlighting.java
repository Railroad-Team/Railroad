package dev.railroadide.railroad.ide.syntaxhighlighting;

import dev.railroadide.railroad.Railroad;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTreeCursor;
import org.treesitter.TreeSitterJava;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TreeSitterJavaSyntaxHighlighting {
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        long start = System.currentTimeMillis();

        var highlighter = new SyntaxHighlighter(text);
        highlighter.traverseTree(new TSTreeCursor(highlighter.rootNode), highlighter.rootNode);

        var styles = highlighter.spansBuilder.create();
        Railroad.LOGGER.debug("Computed highlighting in {} ms", System.currentTimeMillis() - start);
        return styles;
    }

    private static class SyntaxHighlighter {
        private final StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        private final String text;
        private final TSNode rootNode;
        private final List<TSNode> nodes = new ArrayList<>();
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

                if (!cursor.gotoFirstChild()) {
                    nodes.add(currentNode);

                    //String atNode = text.substring(start, end);
                    //System.out.println(type + ": " + atNode);

                    typeSwitch:
                    switch (type) {
                        case "line_comment" -> spansBuilder.add(Collections.singleton("comment"), end - start);
                        case "decimal_integer_literal", "decimal_floating_point_literal" ->
                            spansBuilder.add(Collections.singleton("number"), end - start);
                        case "identifier" -> {
                            // check to see if the identifier is a class name

                            int i = nodes.size() - 1;
                            while (i >= 0) {
                                TSTreeCursor copy = cursor.copy();
                                if (i == nodes.size() - 1) {
                                    copy.gotoParent();
                                    copy.gotoNextSibling();
                                    copy.gotoFirstChild();
                                    TSNode nextNode = copy.currentNode();
                                    if (nextNode == null || nextNode.getType().equals(".")) {
                                        break;
                                    }
                                }

                                TSNode n = nodes.get(i);
                                if (n.getType().equals("identifier") || n.getType().equals(".")) {
                                    i--;
                                } else if (n.getType().equals("import")) {
                                    spansBuilder.add(Collections.singleton("type"), end - start);
                                    break typeSwitch;
                                } else {
                                    break;
                                }
                            }

                            spansBuilder.add(Collections.singleton("name"), end - start);
                        }
                        case "type_identifier" -> spansBuilder.add(Collections.singleton("type"), end - start);
                        case "package" -> spansBuilder.add(Collections.singleton("package"), end - start);
                        case "public", "class", "implements", "static", "final", "private", "protected", "return",
                             "void_type", "int_type", "double_type", "float_type", "short_type", "byte_type",
                             "long_type", "boolean_type", "char_type", "instanceof", "if", "for", "do", "while",
                             "new" -> spansBuilder.add(Collections.singleton("modifier"), end - start);
                        case "import" -> spansBuilder.add(Collections.singleton("import"), end - start);
                        case "string_fragment", "\"" -> spansBuilder.add(Collections.singleton("string"), end - start);
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
