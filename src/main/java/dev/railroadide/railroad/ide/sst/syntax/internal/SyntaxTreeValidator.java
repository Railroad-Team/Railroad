package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;

import java.util.List;
import java.util.Optional;

final class SyntaxTreeValidator {
    private SyntaxTreeValidator() {
    }

    static void validate(SyntaxNode root) {
        if (root.start() != 0)
            throw new IllegalStateException("syntax root must start at 0, got " + root.start());

        validateNode(root, null);
    }

    private static void validateNode(SyntaxNode node, SyntaxNode expectedParent) {
        if (node.start() < 0)
            throw new IllegalStateException("node start cannot be negative: " + node.kind().id());
        if (node.end() < node.start())
            throw new IllegalStateException("node end cannot be before start: " + node.kind().id());

        Optional<SyntaxNode> parent = node.parent();
        if (expectedParent == null) {
            if (parent.isPresent())
                throw new IllegalStateException("root node must not have a parent");
        } else {
            if (parent.isEmpty() || parent.get() != expectedParent)
                throw new IllegalStateException("invalid parent link at node " + node.kind().id());
        }

        List<SyntaxNode> children = node.children();
        if (node instanceof SyntaxToken) {
            if (!children.isEmpty())
                throw new IllegalStateException("token node cannot have children: " + node.kind().id());
            return;
        }

        int childCursor = node.start();
        for (SyntaxNode child : children) {
            if (child.start() != childCursor) {
                throw new IllegalStateException(
                        "child start mismatch in node " + node.kind().id() +
                                ": expected " + childCursor + ", got " + child.start()
                );
            }

            validateNode(child, node);
            childCursor = child.end();
        }

        if (childCursor != node.end()) {
            throw new IllegalStateException(
                    "child range mismatch in node " + node.kind().id() +
                            ": expected end " + node.end() + ", got " + childCursor
            );
        }
    }
}
