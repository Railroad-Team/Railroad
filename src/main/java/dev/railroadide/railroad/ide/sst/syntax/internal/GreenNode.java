package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Immutable internal syntax node whose width is the sum of its ordered children.
 */
@ApiStatus.Internal
public final class GreenNode extends GreenElement {
    private final List<GreenElement> children;

    /**
     * Creates a node by copying ordered children and summing their text widths.
     *
     * @param kind the node's syntax kind
     * @param children the nonnull child elements in source order
     */
    public GreenNode(SyntaxKind kind, List<? extends GreenElement> children) {
        super(kind, sumWidths(children));
        Objects.requireNonNull(children, "children");
        this.children = List.copyOf(children);
    }

    /**
     * Returns the node's immutable child sequence.
     *
     * @return the child elements in source order
     */
    public List<GreenElement> children() {
        return children;
    }

    private static int sumWidths(List<? extends GreenElement> children) {
        Objects.requireNonNull(children, "children");
        int sum = 0;
        for (GreenElement child : children) {
            Objects.requireNonNull(child, "children cannot contain nulls");
            sum += child.width();
        }

        return sum;
    }

    /**
     * Creates a generic root node from the supplied ordered children.
     *
     * @param children the child elements in source order
     * @return the new green root node
     */
    public static GreenNode root(List<? extends GreenElement> children) {
        return new GreenNode(SyntaxKind.ROOT, children);
    }
}
