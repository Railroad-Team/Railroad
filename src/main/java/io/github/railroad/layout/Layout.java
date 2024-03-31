package io.github.railroad.layout;

import io.github.railroad.utility.Tree;

public class Layout {
    private final Tree<LayoutItem> tree;

    public Layout(Tree<LayoutItem> tree) {
        this.tree = tree;
    }

    public void print() {
        tree.print();
    }
}
