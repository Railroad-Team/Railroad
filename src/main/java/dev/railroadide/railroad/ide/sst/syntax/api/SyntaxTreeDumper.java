package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.List;
import java.util.Objects;

/**
 * Debug helper for deterministic syntax-tree snapshots in tests.
 * <p>
 * This class is intended for assertions, fixtures, and manual debugging rather than user
 * facing rendering. Token text is escaped so snapshots stay stable and readable.
 */
public final class SyntaxTreeDumper {
    private static final String INDENT = "  ";

    private SyntaxTreeDumper() {
    }

    /**
     * Dumps a full tree starting at its root.
     *
     * @param tree syntax tree to dump
     * @return deterministic textual dump of the tree
     * @throws NullPointerException if {@code tree} is {@code null}
     */
    public static String dump(SyntaxTree tree) {
        Objects.requireNonNull(tree, "tree");
        return dump(tree.root());
    }

    /**
     * Dumps a subtree rooted at the supplied node.
     *
     * @param root subtree root
     * @return deterministic textual dump of the subtree
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static String dump(SyntaxNode root) {
        Objects.requireNonNull(root, "root");
        StringBuilder builder = new StringBuilder();
        appendNode(root, builder, 0);
        return builder.toString();
    }

    private static void appendNode(SyntaxNode node, StringBuilder builder, int depth) {
        builder.append(INDENT.repeat(Math.max(0, depth)))
                .append(node.kind().id())
                .append(" [")
                .append(node.start())
                .append(",")
                .append(node.end())
                .append(')');

        if (node instanceof SyntaxToken token) {
            builder.append(' ')
                    .append('"')
                    .append(escape(token.text()))
                    .append('"');
        }

        builder.append('\n');

        List<SyntaxNode> children = node.children();
        for (SyntaxNode child : children) {
            appendNode(child, builder, depth + 1);
        }
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\\') {
                escaped.append("\\\\");
            } else if (ch == '"') {
                escaped.append("\\\"");
            } else if (ch == '\n') {
                escaped.append("\\n");
            } else if (ch == '\r') {
                escaped.append("\\r");
            } else if (ch == '\t') {
                escaped.append("\\t");
            } else {
                escaped.append(ch);
            }
        }

        return escaped.toString();
    }
}
