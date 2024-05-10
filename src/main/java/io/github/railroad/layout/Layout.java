package io.github.railroad.layout;

import io.github.railroad.utility.NodeTree;

public class Layout {
    private final NodeTree<LayoutItem> tree;

    public Layout(NodeTree<LayoutItem> tree) {
        this.tree = tree;
    }

    public void print() {
        tree.print();
    }
}
