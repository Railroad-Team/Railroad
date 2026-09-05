package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Positioned syntax node that lazily materializes and caches its child views.
 */
public final class RedNode extends RedElement {
    private volatile List<SyntaxNode> children;

    /**
     * Creates a positioned syntax view over the supplied green element.
     *
     * @param green the immutable backing element
     * @param parent the containing syntax node, or {@code null} for a root
     * @param start the nonnegative absolute UTF-16 start offset
     */
    public RedNode(GreenNode green, RedNode parent, int start) {
        super(green, parent, start);
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> cached = children;
        if (cached != null)
            return cached;

        synchronized (this) {
            if (children != null)
                return children;

            children = buildChildren();
            return children;
        }
    }

    private List<SyntaxNode> buildChildren() {
        GreenNode greenNode = (GreenNode) green();
        List<GreenElement> greenChildren = greenNode.children();
        List<SyntaxNode> built = new ArrayList<>(greenChildren.size());

        int childStart = start();
        for (GreenElement child : greenChildren) {
            built.add(RedFactory.create(child, this, childStart));
            childStart += child.width();
        }

        return List.copyOf(built);
    }
}
