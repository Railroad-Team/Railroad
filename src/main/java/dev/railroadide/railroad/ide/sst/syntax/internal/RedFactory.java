package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

/**
 * Creates positioned syntax views over immutable green nodes and tokens.
 */
public final class RedFactory {
    private RedFactory() {
    }

    /**
     * Wraps a green root in a parentless syntax view starting at offset zero.
     *
     * @param root the immutable green root
     * @return the positioned root view
     */
    public static RedNode root(GreenNode root) {
        return new RedNode(root, null, 0);
    }

    /**
     * Creates the appropriate positioned node or token view for a green element.
     *
     * @param green the immutable element to wrap
     * @param parent the containing syntax node, or {@code null} for a root
     * @param start the nonnegative absolute UTF-16 start offset
     * @return the positioned syntax view
     */
    public static SyntaxNode create(GreenElement green, RedNode parent, int start) {
        if (green instanceof GreenToken greenToken)
            return new RedToken(greenToken, parent, start);

        return new RedNode((GreenNode) green, parent, start);
    }
}
