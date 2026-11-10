package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Checks syntax-tree ranges, child ordering, token leaves, and parent-link consistency.
 */
public final class SyntaxTreeValidator {
    private SyntaxTreeValidator() {
    }

    /**
     * Validates root position, parent links, contiguous child ranges, and token leaves, throwing on inconsistency.
     *
     * @param root the parentless syntax root expected to begin at offset zero
     * @throws IllegalStateException if a source range, parent link, or child sequence is inconsistent
     */
    public static void validate(SyntaxNode root) {
        validate(root, () -> {
        });
    }

    /**
     * Validates a tree with cooperative checks during traversal and internal child construction.
     * Callback failures propagate unchanged and do not invalidate already completed child
     * caches. A cancelled traversal may be retried with an active callback.
     *
     * <p>
     * Internal {@link RedNode} instances receive the callback when materializing their
     * children. Other node implementations must bound their own {@link SyntaxNode#children()}
     * work; this traversal cannot interrupt an uncooperative provider method.
     * The callback is never retained by the validated tree.
     *
     * @param root parentless syntax root expected to begin at offset zero
     * @param activityCheck request activity check, invoked on the validating thread
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalStateException if a source range, parent link, or child sequence is inconsistent
     */
    public static void validate(SyntaxNode root, Runnable activityCheck) {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(activityCheck, "activityCheck cannot be null");
        if (root.start() != 0)
            throw new IllegalStateException("syntax root must start at 0, got " + root.start());

        validateNode(root, null, activityCheck);
    }

    private static void validateNode(SyntaxNode node, SyntaxNode expectedParent, Runnable activityCheck) {
        ActivityCheckpoint.poll(activityCheck);

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

        List<SyntaxNode> children = node instanceof RedNode rNode ? rNode.children(activityCheck) : node.children();
        if (node instanceof SyntaxToken) {
            if (!children.isEmpty())
                throw new IllegalStateException("token node cannot have children: " + node.kind().id());

            return;
        }

        int childCursor = node.start();
        for (SyntaxNode child : children) {
            ActivityCheckpoint.poll(activityCheck);

            if (child.start() != childCursor)
                throw new IllegalStateException("child start mismatch in node " + node.kind().id() +
                    ": expected " + childCursor + ", got " + child.start());

            validateNode(child, node, activityCheck);
            childCursor = child.end();
        }

        if (childCursor != node.end())
            throw new IllegalStateException("child range mismatch in node " + node.kind().id() +
                ": expected end " + node.end() + ", got " + childCursor);
    }
}
