package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Internal bridge for constructing public {@link SyntaxTree} instances from
 * green/red implementation types.
 */
@ApiStatus.Internal
public final class SyntaxInternalFactory {
    private SyntaxInternalFactory() {
    }

    public static GreenToken greenToken(SyntaxKind kind, String text) {
        return new GreenToken(kind, text);
    }

    public static GreenNode greenNode(SyntaxKind kind, List<? extends GreenElement> children) {
        Objects.requireNonNull(children, "children");
        return new GreenNode(kind, children);
    }

    public static SyntaxTree treeFromRootChildren(List<? extends GreenElement> children) {
        Objects.requireNonNull(children, "children");
        GreenNode rootGreen = GreenNode.root(children);
        SyntaxTree tree = new SyntaxTree(RedFactory.root(rootGreen));
        SyntaxTreeValidator.validate(tree.root());
        return tree;
    }

    public static SyntaxTree treeFromGreenRoot(GreenNode root) {
        SyntaxTree tree = new SyntaxTree(RedFactory.root(Objects.requireNonNull(root, "root")));
        SyntaxTreeValidator.validate(tree.root());
        return tree;
    }

    public static GreenNode greenRoot(SyntaxTree tree) {
        Objects.requireNonNull(tree, "tree");
        return greenNode(tree.root());
    }

    public static GreenNode greenNode(SyntaxNode node) {
        GreenElement element = greenElement(node);
        if (element instanceof GreenNode greenNode)
            return greenNode;

        throw new IllegalArgumentException("syntax node is not backed by a GreenNode: " + node.kind().id());
    }

    public static GreenElement greenElement(SyntaxNode node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof RedElement redElement)
            return redElement.green();

        throw new IllegalArgumentException("syntax node is not backed by internal red element: " + node.getClass().getName());
    }
}
