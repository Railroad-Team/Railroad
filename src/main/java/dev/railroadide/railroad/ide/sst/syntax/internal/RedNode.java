package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.ArrayList;
import java.util.List;

final class RedNode extends RedElement {
    private volatile List<SyntaxNode> children;

    RedNode(GreenNode green, RedNode parent, int start) {
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
