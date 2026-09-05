package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

public final class RedFactory {
    private RedFactory() {
    }

    public static RedNode root(GreenNode root) {
        return new RedNode(root, null, 0);
    }

    public static SyntaxNode create(GreenElement green, RedNode parent, int start) {
        if (green instanceof GreenToken greenToken)
            return new RedToken(greenToken, parent, start);

        return new RedNode((GreenNode) green, parent, start);
    }
}
